package com.yowyob.tiibntick.core.billing.wallet.adapter.in.web;

import com.yowyob.tiibntick.core.billing.wallet.adapter.in.web.dto.request.CreditWalletRequest;
import com.yowyob.tiibntick.core.billing.wallet.adapter.in.web.dto.request.InitiatePaymentRequest;
import com.yowyob.tiibntick.core.billing.wallet.adapter.in.web.dto.response.PaymentIntentResponse;
import com.yowyob.tiibntick.core.billing.wallet.adapter.in.web.dto.response.WalletBalanceResponse;
import com.yowyob.tiibntick.core.billing.wallet.adapter.in.web.dto.response.WalletTransactionResponse;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.CreditWalletCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.*;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentSplitResult;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * WalletController — WebFlux REST controller exposing wallet endpoints.
 *
 * Base path: /billing/wallet
 *
 * @author MANFOUO Braun
 */
@Slf4j
@RestController
@RequestMapping("/billing/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final IWalletUseCase walletUseCase;

    /**
     * GET /billing/wallet/{userId}/balance?tenantId={tenantId}
     * Returns the available balance for a user's wallet.
     */
    @GetMapping("/{userId}/balance")
    public Mono<WalletBalanceResponse> getBalance(
            @PathVariable UUID userId,
            @RequestParam UUID tenantId) {
        log.debug("GET balance userId={}", userId);
        return walletUseCase.getOrCreateWallet(userId, tenantId)
                .map(wallet -> new WalletBalanceResponse(
                        wallet.getId().value(),
                        wallet.getUserId(),
                        wallet.getBalance().amount(),
                        wallet.getReservedBalance().amount(),
                        wallet.getCurrency().getCurrencyCode(),
                        wallet.getStatus().name()));
    }

    /**
     * POST /billing/wallet/{userId}/credit?tenantId={tenantId}
     * Credits an amount to the wallet (admin top-up, refund, etc.).
     */
    @PostMapping("/{userId}/credit")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<WalletTransactionResponse> creditWallet(
            @PathVariable UUID userId,
            @RequestParam UUID tenantId,
            @Valid @RequestBody CreditWalletRequest request) {
        log.info("POST credit userId={} amount={}", userId, request.amount());
        return walletUseCase.creditWallet(new CreditWalletCommand(
                        userId, tenantId,
                        Money.of(request.amount(), request.currency()),
                        request.referenceId(), request.description()))
                .map(this::toTransactionResponse);
    }

    /**
     * POST /billing/wallet/pay?tenantId={tenantId}&userId={userId}
     * Initiates an asynchronous payment (MTN MoMo, Orange Money, Stripe).
     * Returns HTTP 202 Accepted with a PaymentIntent (status=PENDING).
     */
    @PostMapping("/pay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<PaymentIntentResponse> initiatePayment(
            @RequestParam UUID userId,
            @RequestParam UUID tenantId,
            @Valid @RequestBody InitiatePaymentRequest request) {
        log.info("POST pay userId={} invoiceId={} channel={}", userId, request.invoiceId(), request.channel());
        return walletUseCase.initiatePayment(new InitiatePaymentCommand(
                        userId, tenantId,
                        request.invoiceId(),
                        Money.of(request.amount(), request.currency()),
                        request.channel(),
                        request.payerPhone(),
                        request.callbackUrl(),
                        request.description()))
                .map(intent -> new PaymentIntentResponse(
                        intent.getId().value(),
                        intent.getInvoiceId(),
                        intent.getAmount().amount(),
                        intent.getAmount().currencyCode(),
                        intent.getChannel().name(),
                        intent.getStatus().name(),
                        intent.getExternalRef(),
                        buildPaymentMessage(intent),
                        intent.getExpiresAt()));
    }

    /**
     * GET /billing/wallet/{userId}/transactions?tenantId={tenantId}
     * Returns the transaction history for a user's wallet (most recent first).
     */
    @GetMapping("/{userId}/transactions")
    public Flux<WalletTransactionResponse> getTransactions(
            @PathVariable UUID userId,
            @RequestParam UUID tenantId) {
        log.debug("GET transactions userId={}", userId);
        return walletUseCase.getTransactionHistory(userId, tenantId)
                .map(this::toTransactionResponse);
    }

    /**
     * PUT /billing/wallet/{userId}/freeze?tenantId={tenantId}
     * Freezes the wallet (admin action).
     */
    @PutMapping("/{userId}/freeze")
    public Mono<WalletBalanceResponse> freezeWallet(
            @PathVariable UUID userId,
            @RequestParam UUID tenantId) {
        log.info("PUT freeze userId={}", userId);
        return walletUseCase.freezeWallet(userId, tenantId)
                .map(wallet -> new WalletBalanceResponse(
                        wallet.getId().value(), wallet.getUserId(),
                        wallet.getBalance().amount(), wallet.getReservedBalance().amount(),
                        wallet.getCurrency().getCurrencyCode(), wallet.getStatus().name()));
    }

    /**
     * PUT /billing/wallet/{userId}/unfreeze?tenantId={tenantId}
     * Unfreezes the wallet (admin action).
     */
    @PutMapping("/{userId}/unfreeze")
    public Mono<WalletBalanceResponse> unfreezeWallet(
            @PathVariable UUID userId,
            @RequestParam UUID tenantId) {
        log.info("PUT unfreeze userId={}", userId);
        return walletUseCase.unfreezeWallet(userId, tenantId)
                .map(wallet -> new WalletBalanceResponse(
                        wallet.getId().value(), wallet.getUserId(),
                        wallet.getBalance().amount(), wallet.getReservedBalance().amount(),
                        wallet.getCurrency().getCurrencyCode(), wallet.getStatus().name()));
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private WalletTransactionResponse toTransactionResponse(WalletTransaction tx) {
        return new WalletTransactionResponse(
                tx.getId().value(),
                tx.getType().name(),
                tx.getAmount().amount(),
                tx.getBalanceAfter().amount(),
                tx.getAmount().currencyCode(),
                tx.getChannel().name(),
                tx.getReferenceId(),
                tx.getExternalRef(),
                tx.getStatus().name(),
                tx.getDescription(),
                tx.getCreatedAt(),
                tx.getProcessedAt());
    }

    private String buildPaymentMessage(PaymentIntent intent) {
        return switch (intent.getChannel()) {
            case MTN_MOMO -> "USSD push sent to payer's phone. Please confirm within 5 minutes.";
            case ORANGE_MONEY -> "Orange Money payment initiated. Complete via redirect URL.";
            case STRIPE -> "Stripe payment intent created. Use client_secret to confirm.";
            default -> "Payment initiated. Awaiting confirmation.";
        };
    }
    // ── : FreelancerOrg wallet endpoints ─────────────────────────────────

    /**
     * POST /billing/wallet/freelancer-org
     * Creates a dedicated wallet for a FreelancerOrganization.
     */
    @PostMapping("/freelancer-org")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<com.yowyob.tiibntick.core.billing.wallet.domain.model.Wallet> createFreelancerOrgWallet(
            @RequestParam String freelancerOrgId,
            @RequestParam UUID tenantId,
            @RequestParam(defaultValue = "XAF") String currency) {
        log.info("POST /billing/wallet/freelancer-org orgId={}", freelancerOrgId);
        return walletUseCase.createFreelancerOrgWallet(
                new CreateFreelancerOrgWalletCommand(freelancerOrgId, tenantId, currency));
    }

    /**
     * POST /billing/wallet/split-revenue
     * Splits mission revenue between platform, FreelancerOrg, and optional sub-deliverer.
     */
    @PostMapping("/split-revenue")
    public Mono<PaymentSplitResult> splitMissionRevenue(
            @RequestBody SplitRevenueRequest request) {
        log.info("POST /billing/wallet/split-revenue missionId={}", request.missionId());
        return walletUseCase.splitMissionRevenue(new SplitMissionRevenueCommand(
                request.missionId(), request.totalAmount(), request.freelancerOrgId(),
                request.tenantId(), request.subDelivererId(),
                request.platformCommissionRate() != null ? request.platformCommissionRate() : 0.05,
                request.subDelivererCommissionRate() != null ? request.subDelivererCommissionRate() : 0.0));
    }

    /**
     * POST /billing/wallet/transfer-commission
     * Transfers sub-deliverer commission from org wallet to sub-deliverer personal wallet.
     */
    @PostMapping("/transfer-commission")
    public Mono<WalletTransactionResponse> transferSubDelivererCommission(
            @RequestParam String freelancerOrgId,
            @RequestParam String subDelivererId,
            @RequestParam java.math.BigDecimal amount,
            @RequestParam String missionId,
            @RequestParam UUID tenantId) {
        log.info("POST /billing/wallet/transfer-commission orgId={} subId={} amount={}",
                freelancerOrgId, subDelivererId, amount);
        return walletUseCase.transferSubDelivererCommission(
                new TransferSubDelivererCommissionCommand(
                        freelancerOrgId, subDelivererId, amount, missionId, tenantId))
                .map(this::toTransactionResponse);
    }

    /** Request body for split-revenue endpoint. */
    public record SplitRevenueRequest(
            String missionId,
            java.math.BigDecimal totalAmount,
            String freelancerOrgId,
            UUID tenantId,
            String subDelivererId,
            Double platformCommissionRate,
            Double subDelivererCommissionRate
    ) {}

}