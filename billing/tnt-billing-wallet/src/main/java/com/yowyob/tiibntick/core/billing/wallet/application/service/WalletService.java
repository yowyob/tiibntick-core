package com.yowyob.tiibntick.core.billing.wallet.application.service;

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
    private final IMoMoPaymentPort moMoPaymentPort;
    private final IIdempotencyStore idempotencyStore;
    private final IWalletEventPublisher eventPublisher;
    private final IWalletNotificationPort notificationPort;

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
                            .doOnSuccess(saved -> publishCreditEvents(wallet));
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
                            .doOnSuccess(saved -> publishDebitEvents(wallet));
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
                .flatMap(intent -> {
                    if (!intent.isPending()) {
                        log.warn("Received duplicate callback for intent {} status={}",
                                intent.getId(), intent.getStatus());
                        return Mono.just(intent);
                    }
                    boolean success = "SUCCESSFUL".equalsIgnoreCase(command.providerStatus());
                    if (success) {
                        return handleSuccessfulPayment(intent, command.financialTransactionId());
                    } else {
                        return handleFailedPayment(intent, command.failureReason());
                    }
                });
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
                    
                    return creditOrgMono.then(creditSubMono)
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
                    return paymentIntentRepository.save(intent)
                            .flatMap(savedIntent -> dispatchToProvider(savedIntent, request, wallet))
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

    private Mono<PaymentIntent> dispatchToProvider(PaymentIntent intent, PaymentRequest request, Wallet wallet) {
        MoMoPayload payload = MoMoPayload.fromRequest(request, UUID.randomUUID().toString());
        Mono<PaymentIntent> dispatch = switch (request.channel()) {
            case MTN_MOMO    -> moMoPaymentPort.initiateMtnCollection(payload, UUID.randomUUID().toString())
                                               .thenReturn(intent);
            case ORANGE_MONEY -> moMoPaymentPort.initiateOrangePayment(payload).thenReturn(intent);
            case STRIPE      -> moMoPaymentPort.initiateStripePayment(payload, request.idempotencyKey())
                                               .thenReturn(intent);
            default          -> Mono.just(intent);
        };
        return dispatch.onErrorResume(e -> {
            log.warn("Payment provider dispatch failed (non-fatal in dev): {}", e.getMessage());
            return Mono.just(intent);
        });
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
                            .then(notificationPort.sendPaymentConfirmed(
                                    wallet.getUserId(),
                                    String.format("✅ Paiement %s confirmé.", intent.getAmount())))
                            .thenReturn(intent);
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

    private void publishCreditEvents(Wallet wallet) {
        wallet.getPendingCreditEvents().forEach(event -> eventPublisher.publish(event).subscribe());
        wallet.getPendingCreditEvents().clear();
    }

    private void publishDebitEvents(Wallet wallet) {
        wallet.getPendingDebitEvents().forEach(event -> eventPublisher.publish(event).subscribe());
        wallet.getPendingDebitEvents().clear();
    }
}
