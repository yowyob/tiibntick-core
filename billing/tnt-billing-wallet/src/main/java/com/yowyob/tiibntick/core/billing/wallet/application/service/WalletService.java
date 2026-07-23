package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.dto.KernelPaymentOrderDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.*;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.*;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import com.yowyob.tiibntick.core.billing.wallet.domain.event.*;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.*;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.*;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.WalletOwnerType;

import java.util.Currency;
import java.util.UUID;

/**
 * WalletService — application service orchestrating all wallet operations.
 * Implements IWalletUseCase. Coordinates domain logic, persistence,
 * Mobile Money API calls, idempotency, and event publishing.
 *
 * <p>All sensitive financial operations are protected by {@link RequirePermission}
 * annotations from {@code tnt-roles-core}. The AOP aspect checks the reactive
 * security context before delegating to the method body.</p>
 *
 * <p>Permission mapping:</p>
 * <ul>
 *   <li>{@code wallet:read}    — balance and transaction history queries</li>
 *   <li>{@code wallet:write}   — credit/debit operations</li>
 *   <li>{@code payment:process} — initiate / confirm / refund payments</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService implements IWalletUseCase {

    private static final Duration PAYMENT_IDEMPOTENCY_TTL = Duration.ofMinutes(5);
    private static final Duration RESULT_CACHE_TTL = Duration.ofHours(24);
    private static final int PAYMENT_INTENT_TTL_MINUTES = 5;
    private static final Currency DEFAULT_CURRENCY = Currency.getInstance("XAF");

    private final IWalletRepository walletRepository;
    private final IPaymentIntentRepository paymentIntentRepository;
    private final IIdempotencyStore idempotencyStore;
    private final IWalletEventPublisher eventPublisher;
    private final IWalletNotificationPort notificationPort;
    private final IPaymentAnchorPort paymentAnchorPort;
    private final IKernelPaymentGatewayPort kernelPaymentGatewayPort;

    @Override
    @RequirePermission(resource = "wallet", action = "read")
    public Mono<Wallet> getOrCreateWallet(UUID userId, UUID tenantId) {
        return walletRepository.findByUserId(userId, tenantId)
                .switchIfEmpty(Mono.defer(() -> createAndSaveWallet(userId, tenantId)));
    }

    @Override
    @RequirePermission(resource = "wallet", action = "read")
    public Mono<Money> getBalance(UUID userId, UUID tenantId) {
        return getOrCreateWallet(userId, tenantId)
                .map(Wallet::availableBalance);
    }

    @Override
    @Transactional
    @RequirePermission(resource = "wallet", action = "write")
    public Mono<WalletTransaction> creditWallet(CreditWalletCommand command) {
        return getOrCreateWallet(command.userId(), command.tenantId())
                .flatMap(wallet -> {
                    WalletTransaction tx = wallet.credit(
                            command.amount(), command.referenceId(), command.description());
                    return walletRepository.save(wallet)
                            .then(walletRepository.saveTransaction(tx))
                            .flatMap(saved -> publishCreditEvents(wallet).thenReturn(saved));
                });
    }

    @Override
    @Transactional
    @RequirePermission(resource = "wallet", action = "write")
    public Mono<WalletTransaction> debitWallet(DebitWalletCommand command) {
        return getOrCreateWallet(command.userId(), command.tenantId())
                .flatMap(wallet -> {
                    WalletTransaction tx = wallet.debit(
                            command.amount(), command.referenceId(),
                            command.channel(), command.description(), command.idempotencyKey());
                    return walletRepository.save(wallet)
                            .then(walletRepository.saveTransaction(tx))
                            .flatMap(saved -> publishDebitEvents(wallet).thenReturn(saved));
                });
    }

    @Override
    @Transactional
    @RequirePermission(resource = "payment", action = "process")
    public Mono<WalletTransaction> creditCommission(CreditCommissionCommand command) {
        return getOrCreateWallet(command.delivererId(), command.tenantId())
                .flatMap(wallet -> {
                    WalletTransaction tx = wallet.creditCommission(
                            command.commissionAmount(), command.missionId());
                    return walletRepository.save(wallet)
                            .then(walletRepository.saveTransaction(tx))
                            .then(eventPublisher.publish(new CommissionCalculated(
                                    command.delivererId(), command.tenantId(),
                                    command.missionId(), command.invoiceId(),
                                    command.commissionAmount(), command.commissionAmount())))
                            .thenReturn(tx);
                });
    }

    @Override
    @Transactional
    @RequirePermission(resource = "payment", action = "process")
    public Mono<PaymentIntent> initiatePayment(InitiatePaymentCommand command) {
        String idempotencyKey = PaymentRequest.buildIdempotencyKey(command.invoiceId(), command.channel());

        return idempotencyStore.getResult(idempotencyKey)
                .flatMap(existingIntentId ->
                        paymentIntentRepository.findById(PaymentIntentId.of(existingIntentId))
                                .switchIfEmpty(Mono.error(new PaymentIntentNotFoundException(existingIntentId))))
                .switchIfEmpty(
                        idempotencyStore.tryAcquire(idempotencyKey, PAYMENT_IDEMPOTENCY_TTL)
                                .flatMap(acquired -> {
                                    if (!acquired) {
                                        return Mono.error(new DuplicatePaymentException(idempotencyKey));
                                    }
                                    return doInitiatePayment(command, idempotencyKey);
                                })
                );
    }

    @Override
    @Transactional
    @RequirePermission(resource = "payment", action = "process")
    public Mono<PaymentIntent> handlePaymentCallback(ConfirmPaymentCommand command) {
        return paymentIntentRepository.findByExternalRef(command.externalRef())
                .switchIfEmpty(Mono.error(new PaymentIntentNotFoundException(command.externalRef())))
                .flatMap(intent -> applyProviderStatus(intent, command.providerStatus(),
                        command.financialTransactionId(), command.failureReason()));
    }

    /**
     * Shared terminal-status handling for a PaymentIntent, whichever path discovered the
     * outcome — the original webhook-driven {@link #handlePaymentCallback} above, or the
     * Kernel order-reconciliation poller ({@link #pollPendingProviderOrders}). Called via
     * plain self-invocation (not through the {@code IWalletUseCase} proxy) so the scheduled
     * poller — which runs with no inbound request/security context — is never subject to
     * {@link RequirePermission}'s reactive-context check.
     */
    private Mono<PaymentIntent> applyProviderStatus(PaymentIntent intent, String providerStatus,
                                                     String financialTransactionId, String failureReason) {
        if (!intent.isPending()) {
            log.warn("Received duplicate confirmation for intent {} status={}",
                    intent.getId(), intent.getStatus());
            return Mono.just(intent);
        }
        boolean success = "SUCCESSFUL".equalsIgnoreCase(providerStatus);
        if (success) {
            return handleSuccessfulPayment(intent, financialTransactionId);
        } else {
            return handleFailedPayment(intent, failureReason);
        }
    }

    @Override
    @Transactional
    @RequirePermission(resource = "payment", action = "refund")
    public Mono<WalletTransaction> refundPayment(RefundPaymentCommand command) {
        return paymentIntentRepository.findById(PaymentIntentId.of(command.paymentIntentId()))
                .switchIfEmpty(Mono.error(new PaymentIntentNotFoundException(command.paymentIntentId().toString())))
                .flatMap(intent -> walletRepository.findById(intent.getWalletId())
                        .switchIfEmpty(Mono.error(new WalletNotFoundException(intent.getWalletId().toString())))
                        .flatMap(wallet -> {
                            WalletTransaction originalTx = wallet.getTransactions().stream()
                                    .filter(tx -> intent.getIdempotencyKey().equals(tx.getIdempotencyKey())
                                            && tx.isConfirmed())
                                    .findFirst()
                                    .orElseThrow(() -> new IllegalStateException(
                                            "No confirmed debit found for intent: " + intent.getId()));

                            WalletTransaction refundTx = originalTx.refund();
                            wallet.credit(refundTx.getAmount(), "REFUND-" + intent.getInvoiceId(),
                                    "Refund: " + command.reason());
                            intent.cancel();

                            return walletRepository.save(wallet)
                                    .then(walletRepository.saveTransaction(refundTx))
                                    .then(paymentIntentRepository.save(intent))
                                    .thenReturn(refundTx);
                        })
                );
    }

    @Override
    @RequirePermission(resource = "wallet", action = "read")
    public Flux<WalletTransaction> getTransactionHistory(UUID userId, UUID tenantId) {
        return walletRepository.findByUserId(userId, tenantId)
                .flatMapMany(wallet -> walletRepository.findTransactionsByWalletId(wallet.getId()));
    }

    @Override
    @Transactional
    @RequirePermission(resource = "wallet", action = "write")
    public Mono<Wallet> freezeWallet(UUID userId, UUID tenantId) {
        return walletRepository.findByUserId(userId, tenantId)
                .switchIfEmpty(Mono.error(new WalletNotFoundException(userId)))
                .flatMap(wallet -> {
                    wallet.freeze();
                    return walletRepository.save(wallet);
                });
    }

    @Override
    @Transactional
    @RequirePermission(resource = "wallet", action = "write")
    public Mono<Wallet> unfreezeWallet(UUID userId, UUID tenantId) {
        return walletRepository.findByUserId(userId, tenantId)
                .switchIfEmpty(Mono.error(new WalletNotFoundException(userId)))
                .flatMap(wallet -> {
                    wallet.unfreeze();
                    return walletRepository.save(wallet);
                });
    }

        @Override
    @Transactional
    @RequirePermission(resource = "wallet", action = "write")
    public Mono<Wallet> createFreelancerOrgWallet(CreateFreelancerOrgWalletCommand command) {
        log.info("Creating FreelancerOrg wallet — orgId={} tenantId={}", 
                command.freelancerOrgId(), command.tenantId());
        
        Wallet orgWallet = Wallet.createForOrg(
                WalletOwnerType.FREELANCER_ORG,
                command.freelancerOrgId(),
                command.tenantId(),
                Currency.getInstance(command.currency() != null ? command.currency() : "XAF"));
        
        return walletRepository.save(orgWallet)
                .doOnSuccess(w -> log.info("Created FreelancerOrg wallet {} for org {}", 
                        w.getId(), command.freelancerOrgId()));
    }

    @Override
    @Transactional
    @RequirePermission(resource = "payment", action = "process")
    public Mono<PaymentSplitResult> splitMissionRevenue(SplitMissionRevenueCommand command) {
        log.info("Splitting mission revenue — missionId={} total={} org={} sub={}",
                command.missionId(), command.totalAmount(), 
                command.freelancerOrgId(), command.subDelivererId());
        
        return walletRepository.findByOwnerId(command.freelancerOrgId(), command.tenantId())
                .switchIfEmpty(Mono.error(new WalletNotFoundException(command.freelancerOrgId())))
                .flatMap(orgWallet -> {
                    // Compute split: platform fee, org revenue, sub-deliverer commission
                    BigDecimal platformFeeRate = BigDecimal.valueOf(command.platformCommissionRate());
                    BigDecimal platformFee = command.totalAmount()
                            .multiply(platformFeeRate)
                            .setScale(2, RoundingMode.HALF_UP);
                    
                    BigDecimal orgRevenue = command.totalAmount().subtract(platformFee);
                    
                    BigDecimal subDelivererShare = (command.subDelivererId() != null && command.subDelivererCommissionRate() > 0)
                            ? orgRevenue.multiply(BigDecimal.valueOf(command.subDelivererCommissionRate()))
                                    .setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    
                    BigDecimal ownerShare = orgRevenue.subtract(subDelivererShare);
                    
                    // Credit org wallet with owner share
                    orgWallet.credit(
                            Money.of(ownerShare, DEFAULT_CURRENCY.getCurrencyCode()),
                            "MISSION-" + command.missionId(),
                            "Mission revenue split — owner share");
                    
                    Mono<Void> creditOrgMono = walletRepository.save(orgWallet).then();
                    
                    // If sub-deliverer exists, credit their personal wallet
                    Mono<Void> creditSubMono = command.subDelivererId() != null && subDelivererShare.compareTo(BigDecimal.ZERO) > 0
                            ? walletRepository.findByOwnerId(command.subDelivererId(), command.tenantId())
                                    .switchIfEmpty(Mono.error(new WalletNotFoundException(command.subDelivererId())))
                                    .flatMap(subWallet -> {
                                        subWallet.credit(
                                                Money.of(subDelivererShare, DEFAULT_CURRENCY.getCurrencyCode()),
                                                "MISSION-" + command.missionId(),
                                                "Sub-deliverer commission split");
                                        return walletRepository.save(subWallet).then();
                                    })
                            : Mono.empty();
                    
                    Mono<Void> publishSplitEvent = eventPublisher.publish(new WalletSplitExecuted(
                            command.missionId(), command.freelancerOrgId(), command.subDelivererId(),
                            command.tenantId(), command.totalAmount(), platformFee,
                            ownerShare, subDelivererShare));

                    return creditOrgMono.then(creditSubMono)
                            .then(publishSplitEvent)
                            .thenReturn(new PaymentSplitResult(
                                    UUID.randomUUID(),
                                    command.missionId(),
                                    command.totalAmount(),
                                    DEFAULT_CURRENCY.getCurrencyCode(),
                                    platformFee,
                                    ownerShare,
                                    subDelivererShare,
                                    command.subDelivererId(),
                                    "COMPLETED"));
                });
    }

    @Override
    @Transactional
    @RequirePermission(resource = "payment", action = "process")
    public Mono<WalletTransaction> transferSubDelivererCommission(
            TransferSubDelivererCommissionCommand command) {
        log.info("Transferring sub-deliverer commission — orgId={} subId={} amount={}",
                command.freelancerOrgId(), command.subDelivererId(), command.amount());
        
        return walletRepository.findByOwnerId(command.freelancerOrgId(), command.tenantId())
                .switchIfEmpty(Mono.error(new WalletNotFoundException(command.freelancerOrgId())))
                .flatMap(orgWallet -> walletRepository.findByOwnerId(command.subDelivererId(), command.tenantId())
                        .switchIfEmpty(Mono.error(new WalletNotFoundException(command.subDelivererId())))
                        .flatMap(subWallet -> {
                            // Debit from org wallet
                            WalletTransaction debitTx = orgWallet.debit(
                                    Money.of(command.amount(), DEFAULT_CURRENCY.getCurrencyCode()),
                                    "SUB-TRANSFER-" + command.missionId(),
                                    PaymentChannel.WALLET,
                                    "Sub-deliverer commission transfer",
                                    UUID.randomUUID().toString());
                            
                            // Credit to sub-deliverer wallet
                            subWallet.credit(
                                    Money.of(command.amount(), DEFAULT_CURRENCY.getCurrencyCode()),
                                    "SUB-TRANSFER-" + command.missionId(),
                                    "Commission received from org");
                            
                            return walletRepository.save(orgWallet)
                                    .then(walletRepository.save(subWallet))
                                    .then(walletRepository.saveTransaction(debitTx))
                                    .doOnSuccess(tx -> log.info("Commission transferred: sub={} amount={}",
                                            command.subDelivererId(), command.amount()))
                                    .thenReturn(debitTx);
                        }));
    }

    // ─── private helpers ───────────────────────────────────────────────────────

    private Mono<Wallet> createAndSaveWallet(UUID userId, UUID tenantId) {
        Wallet newWallet = Wallet.createNew(userId, tenantId, DEFAULT_CURRENCY);
        return walletRepository.save(newWallet)
                .doOnSuccess(w -> log.info("Created new wallet {} for userId={}", w.getId(), userId));
    }

    private Mono<PaymentIntent> doInitiatePayment(InitiatePaymentCommand command, String idempotencyKey) {
        PaymentRequest request = PaymentRequest.builder()
                .invoiceId(command.invoiceId())
                .amount(command.amount())
                .channel(command.channel())
                .payerPhone(command.payerPhone())
                .idempotencyKey(idempotencyKey)
                .callbackUrl(command.callbackUrl())
                .description(command.description())
                .build();

        return getOrCreateWallet(command.userId(), command.tenantId())
                .flatMap(wallet -> {
                    PaymentIntent intent = wallet.initiatePayment(request, PAYMENT_INTENT_TTL_MINUTES);
                    return dispatchToProvider(intent, request, wallet)
                            .flatMap(paymentIntentRepository::save)
                            .flatMap(savedIntent -> {
                                WalletTransaction pendingTx = wallet.createPendingDebit(
                                        request.amount(), request.invoiceId(),
                                        request.channel(), request.description(), idempotencyKey);
                                return walletRepository.save(wallet)
                                        .then(walletRepository.saveTransaction(pendingTx))
                                        .then(idempotencyStore.storeResult(
                                                idempotencyKey, savedIntent.getId().toString(), RESULT_CACHE_TTL))
                                        .then(eventPublisher.publish(new PaymentInitiated(
                                                savedIntent.getId().value(),
                                                wallet.getId().value(),
                                                command.userId(), command.tenantId(),
                                                command.invoiceId(), command.amount(),
                                                command.channel(), command.payerPhone())))
                                        .thenReturn(savedIntent);
                            });
                });
    }

    /**
     * Provider dispatch (Mobile Money/Stripe) used to be handled locally via
     * {@code IMoMoPaymentPort} (MtnMoMoAdapter/OrangeMoneyAdapter/StripeAdapter) — removed as
     * part of the payment/wallet Kernel-delegation workstream (step 6): those adapters had
     * unverified or optional webhook signature checks (Audit n°7 #1/#2/#3) and are deleted
     * outright rather than patched.
     *
     * <p>Step 4 correction (2026-07-18): the Kernel's {@code payment-gateway-controller}
     * ({@code POST /api/payments/orders}) was wrongly believed not to exist — verified
     * present. For channels backed by a real external provider (MTN_MOMO/ORANGE_MONEY via
     * the Kernel's MYCOOLPAY aggregator, STRIPE for cards), this now calls
     * {@link IKernelPaymentGatewayPort#initiateOrder} and records the returned Kernel order
     * id on the intent (via {@link PaymentIntent#attachProviderReference}) so
     * {@link #pollPendingProviderOrders()} can reconcile it later (step 5). Errors propagate
     * (never fail-open) — a failed initiation must not leave behind a PaymentIntent that
     * looks like a real provider dispatch happened.
     *
     * <p>{@code CASH_ON_DELIVERY} and {@code WALLET} never involve an external provider, so
     * no Kernel call is made for them — the intent stays PENDING until confirmed through
     * whatever local flow already applies to those channels (cash receipt recording /
     * in-app wallet-to-wallet transfer confirmation), unchanged by this workstream.
     */
    private Mono<PaymentIntent> dispatchToProvider(PaymentIntent intent, PaymentRequest request, Wallet wallet) {
        ProviderMapping mapping = ProviderMapping.forChannel(request.channel());
        if (mapping == null) {
            log.debug("Channel {} has no external provider — skipping Kernel order dispatch (intentId={})",
                    request.channel(), intent.getId());
            return Mono.just(intent);
        }
        return kernelPaymentGatewayPort.initiateOrder(
                        mapping.provider(), mapping.method(),
                        request.amount().amount(), request.amount().currencyCode(),
                        request.payerPhone(), request.description(), request.callbackUrl(),
                        request.idempotencyKey())
                .doOnNext(order -> {
                    intent.attachProviderReference(order.id());
                    log.info("Dispatched payment intent {} to Kernel provider order {} (provider={}, method={})",
                            intent.getId(), order.id(), mapping.provider(), mapping.method());
                })
                .thenReturn(intent);
    }

    /**
     * Maps a TiiBnTick {@link PaymentChannel} to the {@code provider}/{@code method} pair
     * confirmed by the Kernel's {@code InitiatePaymentRequest} schema enums ({@code provider}:
     * {@code MYCOOLPAY}/{@code STRIPE}; {@code method}: {@code MOBILE_MONEY}/{@code CARD}) —
     * {@code docs/kernel-api/openapi.json}. {@code null} for channels with no external
     * provider ({@code CASH_ON_DELIVERY}, {@code WALLET}).
     */
    private record ProviderMapping(String provider, String method) {
        static ProviderMapping forChannel(PaymentChannel channel) {
            return switch (channel) {
                case MTN_MOMO, ORANGE_MONEY -> new ProviderMapping("MYCOOLPAY", "MOBILE_MONEY");
                case STRIPE -> new ProviderMapping("STRIPE", "CARD");
                case CASH_ON_DELIVERY, WALLET -> null;
            };
        }
    }

    /**
     * Scheduled reconciliation for provider-dispatched payments (workstream step 5). The
     * Kernel documents no outbound webhook back to TiiBnTick for
     * {@code payment-gateway-controller} orders (verified: no such path in
     * {@code docs/kernel-api/openapi.json}), so PaymentIntents dispatched to a real
     * provider by {@link #dispatchToProvider} are reconciled by actively polling
     * {@code POST /api/payments/orders/{id}/refresh} until the Kernel reports a terminal
     * status — the same {@link #applyProviderStatus} path {@link #handlePaymentCallback}
     * uses, so {@code tnt-trust-core}'s {@code PaymentAnchorAdapter} keeps receiving
     * {@code PaymentConfirmed} exactly as before.
     *
     * <p>Runs on a fixed delay (default 20s, configurable via
     * {@code tnt.billing.wallet.kernel.order-poll-interval-ms}) — reasonable backoff for a
     * flow where the user is actively waiting on a Mobile Money USSD prompt or card
     * redirect, without hammering the Kernel every tick regardless of how many orders are
     * in flight. Guarded by ShedLock (same pattern as
     * {@link ReconciliationService#scheduledReconciliation()}) so only one app instance
     * polls at a time in a multi-instance deployment.
     */
    @Scheduled(fixedDelayString = "${tnt.billing.wallet.kernel.order-poll-interval-ms:20000}")
    @SchedulerLock(name = "wallet-payment-order-poll", lockAtMostFor = "PT1M", lockAtLeastFor = "PT5S")
    public void pollPendingProviderOrders() {
        LockAssert.assertLocked();
        reconcilePendingProviderOrders();
    }

    /**
     * The actual reconciliation sweep, split out from {@link #pollPendingProviderOrders()}
     * so it can be unit-tested directly without going through the ShedLock-proxied
     * {@code @Scheduled} entry point (which asserts a live lock via {@link LockAssert}).
     */
    void reconcilePendingProviderOrders() {
        paymentIntentRepository.findAllPendingWithProviderReference()
                .flatMap(this::reconcileProviderOrder)
                .onErrorContinue((error, obj) -> log.warn(
                        "Failed to reconcile a pending Kernel payment order (will retry next tick): {}",
                        error.getMessage()))
                .subscribe();
    }

    private Mono<PaymentIntent> reconcileProviderOrder(PaymentIntent intent) {
        if (intent.isExpired()) {
            intent.expire();
            return paymentIntentRepository.save(intent);
        }
        return kernelPaymentGatewayPort.refreshOrder(intent.getExternalRef())
                .flatMap(order -> reconcileFromOrder(intent, order))
                .switchIfEmpty(Mono.just(intent)); // refreshOrder fail-open on error — retry next tick
    }

    private Mono<PaymentIntent> reconcileFromOrder(PaymentIntent intent, KernelPaymentOrderDto order) {
        String financialTransactionId = order.providerReference() != null
                ? order.providerReference() : order.id();
        if (order.isSuccess()) {
            return applyProviderStatus(intent, "SUCCESSFUL", financialTransactionId, null);
        }
        if (order.isFailure()) {
            return applyProviderStatus(intent, "FAILED", null,
                    "Kernel payment order status: " + order.status());
        }
        return Mono.just(intent); // still in-flight at the provider — retry next tick
    }

    private Mono<PaymentIntent> handleSuccessfulPayment(PaymentIntent intent, String financialTransactionId) {
        intent.confirm(financialTransactionId);
        String idempotencyKey = intent.getIdempotencyKey();

        return walletRepository.findById(intent.getWalletId())
                .switchIfEmpty(Mono.error(new WalletNotFoundException(intent.getWalletId().toString())))
                .flatMap(wallet -> {
                    wallet.getTransactions().stream()
                            .filter(tx -> idempotencyKey.equals(tx.getIdempotencyKey()) && tx.isPending())
                            .findFirst()
                            .ifPresent(tx -> wallet.confirmDebit(tx.getId(), financialTransactionId));

                    return walletRepository.save(wallet)
                            .then(paymentIntentRepository.save(intent))
                            .then(idempotencyStore.release(idempotencyKey))
                            .then(eventPublisher.publish(new PaymentConfirmed(
                                    intent.getId().value(), wallet.getId().value(),
                                    wallet.getUserId(), wallet.getTenantId(),
                                    intent.getInvoiceId(), intent.getAmount(),
                                    intent.getChannel(), financialTransactionId)))
                            .then(anchorPaymentCommit(intent, wallet, financialTransactionId))
                            .then(notificationPort.sendPaymentConfirmed(
                                    wallet.getUserId(),
                                    String.format("✅ Paiement %s confirmé.", intent.getAmount())))
                            .thenReturn(intent);
                });
    }

    /**
     * Anchors the committed payment on the blockchain via {@code tnt-trust-core}, best-effort.
     * A trust-anchoring failure must never fail payment confirmation.
     */
    private Mono<Void> anchorPaymentCommit(PaymentIntent intent, Wallet wallet, String financialTransactionId) {
        PaymentAnchorPayload payload = new PaymentAnchorPayload(
                wallet.getTenantId(), intent.getId().value(), wallet.getId().value(),
                wallet.getUserId(), intent.getInvoiceId(), intent.getAmount().amount(),
                intent.getAmount().currencyCode(), intent.getChannel().name(),
                financialTransactionId, java.time.Instant.now());
        return paymentAnchorPort.anchor(payload)
                .onErrorResume(e -> {
                    log.warn("Failed to anchor payment commit on-chain — paymentIntentId={}: {}",
                            intent.getId().value(), e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<PaymentIntent> handleFailedPayment(PaymentIntent intent, String reason) {
        intent.fail(reason);
        String idempotencyKey = intent.getIdempotencyKey();

        return walletRepository.findById(intent.getWalletId())
                .flatMap(wallet -> {
                    wallet.getTransactions().stream()
                            .filter(tx -> idempotencyKey.equals(tx.getIdempotencyKey()) && tx.isPending())
                            .findFirst()
                            .ifPresent(tx -> tx.fail(reason));

                    return walletRepository.save(wallet)
                            .then(paymentIntentRepository.save(intent))
                            .then(idempotencyStore.release(idempotencyKey))
                            .then(eventPublisher.publish(new PaymentFailed(
                                    intent.getId().value(), wallet.getId().value(),
                                    wallet.getUserId(), wallet.getTenantId(),
                                    intent.getInvoiceId(), intent.getAmount(),
                                    intent.getChannel(), reason)))
                            .then(notificationPort.sendPaymentFailed(
                                    wallet.getUserId(),
                                    String.format("❌ Paiement %s échoué: %s", intent.getAmount(), reason)))
                            .thenReturn(intent);
                });
    }

    /**
     * Publishes pending credit events as part of the caller's reactive chain (and hence the
     * caller's {@code @Transactional} boundary) — previously fire-and-forget
     * ({@code .subscribe()} inside {@code doOnSuccess}), which escaped the transaction and
     * could silently drop events. With the transactional outbox (Chantier C · Audit n°3 · P5)
     * the envelope must be persisted in the same transaction as the wallet write.
     */
    private Mono<Void> publishCreditEvents(Wallet wallet) {
        return Flux.fromIterable(java.util.List.copyOf(wallet.getPendingCreditEvents()))
                .concatMap(eventPublisher::publish)
                .then(Mono.fromRunnable(() -> wallet.getPendingCreditEvents().clear()));
    }

    /** Same contract as {@link #publishCreditEvents(Wallet)}, for debit events. */
    private Mono<Void> publishDebitEvents(Wallet wallet) {
        return Flux.fromIterable(java.util.List.copyOf(wallet.getPendingDebitEvents()))
                .concatMap(eventPublisher::publish)
                .then(Mono.fromRunnable(() -> wallet.getPendingDebitEvents().clear()));
    }
}
