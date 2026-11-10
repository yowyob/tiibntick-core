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
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * WalletController — WebFlux REST controller exposing wallet endpoints.
 *
 * Base path: /billing/wallet
 *
 * <h3>Security model (wallet hardening)</h3>
 * <ul>
 *   <li>The tenant is <strong>always</strong> read from the authenticated JWT
 *       ({@code @CurrentUser TntUserIdentity}) — never from a query parameter, path variable
 *       or request body. A client-supplied {@code tenantId} is ignored.</li>
 *   <li><strong>Self-service</strong> endpoints ({@code /me/**}, {@code /pay}) operate on the
 *       caller's own wallet only; the owner is the JWT {@code sub} (userId). There is no way to
 *       name another owner, which removes the cross-user IDOR.</li>
 *   <li>Every endpoint that names an arbitrary owner ({@code /{userId}/**}), mints or moves money
 *       (credit, split-revenue, transfer-commission) or changes wallet state (freeze, unfreeze,
 *       org wallet creation) requires {@code wallet:admin}. Only {@code TNT_ADMIN} holds it by
 *       default (wildcard) — {@code FREELANCER}/{@code CLIENT} hold {@code wallet:write} and
 *       {@code payment:process}, which must <em>not</em> be enough to credit a wallet.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@RestController
@RequestMapping("/billing/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final IWalletUseCase walletUseCase;

    // ─── self-service (caller's own wallet) ───────────────────────────────

    /**
     * GET /billing/wallet/me/balance — available balance of the caller's wallet.
     * 404 when the caller has no wallet yet — read-only, never creates.
     */
    @GetMapping("/me/balance")
    public Mono<ResponseEntity<WalletBalanceResponse>> getMyBalance(@CurrentUser TntUserIdentity currentUser) {
        return balanceOf(requireUserId(currentUser), requireTenant(currentUser));
    }

    /** GET /billing/wallet/me/transactions — caller's transaction history (most recent first). */
    @GetMapping("/me/transactions")
    public Flux<WalletTransactionResponse> getMyTransactions(@CurrentUser TntUserIdentity currentUser) {
        return walletUseCase.getTransactionHistory(requireUserId(currentUser), requireTenant(currentUser))
                .map(this::toTransactionResponse);
    }

    /**
     * POST /billing/wallet/pay
     * Initiates an asynchronous payment for the <em>caller</em> (Mobile Money / card).
     * Returns HTTP 202 Accepted with a PaymentIntent (status=PENDING). The payer is the JWT
     * identity; the legacy {@code userId}/{@code tenantId} query parameters are ignored.
     */
    @PostMapping("/pay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequirePermission(resource = "payment", action = "process")
    public Mono<PaymentIntentResponse> initiatePayment(
            @CurrentUser TntUserIdentity currentUser,
            @Valid @RequestBody InitiatePaymentRequest request) {
        UUID userId = requireUserId(currentUser);
        log.info("POST pay userId={} invoiceId={} channel={}", userId, request.invoiceId(), request.channel());
        return doInitiatePayment(userId, requireTenant(currentUser), request);
    }

    // ─── administration (any owner / money movement / state change) ───────

    /**
     * GET /billing/wallet/{userId}/balance — balance of any wallet of the caller's tenant.
     * Returns 404 when no wallet exists — read-only, never creates.
     */
    @GetMapping("/{userId}/balance")
    @RequirePermission(resource = "wallet", action = "admin")
    public Mono<ResponseEntity<WalletBalanceResponse>> getBalance(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID userId) {
        log.debug("GET balance userId={}", userId);
        return balanceOf(userId, requireTenant(currentUser));
    }

    /** GET /billing/wallet/{userId}/transactions — history of any wallet of the caller's tenant. */
    @GetMapping("/{userId}/transactions")
    @RequirePermission(resource = "wallet", action = "admin")
    public Flux<WalletTransactionResponse> getTransactions(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID userId) {
        log.debug("GET transactions userId={}", userId);
        return walletUseCase.getTransactionHistory(userId, requireTenant(currentUser))
                .map(this::toTransactionResponse);
    }

    /** POST /billing/wallet/{userId}/credit — admin top-up / refund credit. */
    @PostMapping("/{userId}/credit")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(resource = "wallet", action = "admin")
    public Mono<WalletTransactionResponse> creditWallet(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID userId,
            @Valid @RequestBody CreditWalletRequest request) {
        log.info("POST credit userId={} amount={} by={}", userId, request.amount(), currentUser.userId());
        return walletUseCase.creditWallet(new CreditWalletCommand(
                        userId, requireTenant(currentUser),
                        Money.of(request.amount(), request.currency()),
                        request.referenceId(), request.description()))
                .map(this::toTransactionResponse);
    }

    /**
     * POST /billing/wallet/{userId}/pay — initiates a payment on behalf of another owner
     * (e.g. an agency commission payout). Replaces the former {@code /pay?userId=} form.
     */
    @PostMapping("/{userId}/pay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequirePermission(resource = "wallet", action = "admin")
    public Mono<PaymentIntentResponse> initiatePaymentFor(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID userId,
            @Valid @RequestBody InitiatePaymentRequest request) {
        log.info("POST pay-for userId={} invoiceId={} channel={} by={}",
                userId, request.invoiceId(), request.channel(), currentUser.userId());
        return doInitiatePayment(userId, requireTenant(currentUser), request);
    }

    /** PUT /billing/wallet/{userId}/freeze — freezes the wallet (admin action). */
    @PutMapping("/{userId}/freeze")
    @RequirePermission(resource = "wallet", action = "admin")
    public Mono<WalletBalanceResponse> freezeWallet(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID userId) {
        log.info("PUT freeze userId={} by={}", userId, currentUser.userId());
        return walletUseCase.freezeWallet(userId, requireTenant(currentUser)).map(this::toBalanceResponse);
    }

    /** PUT /billing/wallet/{userId}/unfreeze — unfreezes the wallet (admin action). */
    @PutMapping("/{userId}/unfreeze")
    @RequirePermission(resource = "wallet", action = "admin")
    public Mono<WalletBalanceResponse> unfreezeWallet(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID userId) {
        log.info("PUT unfreeze userId={} by={}", userId, currentUser.userId());
        return walletUseCase.unfreezeWallet(userId, requireTenant(currentUser)).map(this::toBalanceResponse);
    }

    /** POST /billing/wallet/freelancer-org — creates a dedicated wallet for a FreelancerOrganization. */
    @PostMapping("/freelancer-org")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(resource = "wallet", action = "admin")
    public Mono<Wallet> createFreelancerOrgWallet(
            @CurrentUser TntUserIdentity currentUser,
            @RequestParam String freelancerOrgId,
            @RequestParam(defaultValue = "XAF") String currency) {
        log.info("POST /billing/wallet/freelancer-org orgId={}", freelancerOrgId);
        return walletUseCase.createFreelancerOrgWallet(
                new CreateFreelancerOrgWalletCommand(freelancerOrgId, requireTenant(currentUser), currency));
    }

    /**
     * POST /billing/wallet/split-revenue — splits mission revenue between platform, FreelancerOrg
     * and optional sub-deliverer. Money-minting operation: admin only, tenant from the JWT (a
     * {@code tenantId} in the body is ignored).
     */
    @PostMapping("/split-revenue")
    @RequirePermission(resource = "wallet", action = "admin")
    public Mono<PaymentSplitResult> splitMissionRevenue(
            @CurrentUser TntUserIdentity currentUser,
            @RequestBody SplitRevenueRequest request) {
        log.info("POST /billing/wallet/split-revenue missionId={}", request.missionId());
        return walletUseCase.splitMissionRevenue(new SplitMissionRevenueCommand(
                request.missionId(), request.totalAmount(), request.freelancerOrgId(),
                requireTenant(currentUser), request.subDelivererId(),
                request.platformCommissionRate() != null ? request.platformCommissionRate() : 0.05,
                request.subDelivererCommissionRate() != null ? request.subDelivererCommissionRate() : 0.0));
    }

    /**
     * POST /billing/wallet/transfer-commission — transfers sub-deliverer commission from the org
     * wallet to the sub-deliverer's personal wallet. Admin only, tenant from the JWT.
     */
    @PostMapping("/transfer-commission")
    @RequirePermission(resource = "wallet", action = "admin")
    public Mono<WalletTransactionResponse> transferSubDelivererCommission(
            @CurrentUser TntUserIdentity currentUser,
            @RequestParam String freelancerOrgId,
            @RequestParam String subDelivererId,
            @RequestParam java.math.BigDecimal amount,
            @RequestParam String missionId) {
        log.info("POST /billing/wallet/transfer-commission orgId={} subId={} amount={}",
                freelancerOrgId, subDelivererId, amount);
        return walletUseCase.transferSubDelivererCommission(
                new TransferSubDelivererCommissionCommand(
                        freelancerOrgId, subDelivererId, amount, missionId, requireTenant(currentUser)))
                .map(this::toTransactionResponse);
    }

    /** Request body for split-revenue endpoint ({@code tenantId} removed: it comes from the JWT). */
    public record SplitRevenueRequest(
            String missionId,
            java.math.BigDecimal totalAmount,
            String freelancerOrgId,
            String subDelivererId,
            Double platformCommissionRate,
            Double subDelivererCommissionRate
    ) {}

    // ─── helpers ──────────────────────────────────────────────────────────

    /** Tenant of the authenticated caller; 403 when the JWT carries none. */
    private static UUID requireTenant(TntUserIdentity currentUser) {
        if (currentUser == null || currentUser.tenantId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenant in the authenticated context");
        }
        return currentUser.tenantId();
    }

    /** Wallet owner of the authenticated caller (JWT {@code sub}); 403 when absent. */
    private static UUID requireUserId(TntUserIdentity currentUser) {
        if (currentUser == null || currentUser.userId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No user in the authenticated context");
        }
        return currentUser.userId();
    }

    private Mono<ResponseEntity<WalletBalanceResponse>> balanceOf(UUID userId, UUID tenantId) {
        return walletUseCase.findWallet(userId, tenantId)
                .map(wallet -> ResponseEntity.ok(toBalanceResponse(wallet)))
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().<WalletBalanceResponse>build()));
    }

    private Mono<PaymentIntentResponse> doInitiatePayment(UUID userId, UUID tenantId, InitiatePaymentRequest request) {
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

    private WalletBalanceResponse toBalanceResponse(Wallet wallet) {
        return new WalletBalanceResponse(
                wallet.getId().value(), wallet.getUserId(),
                wallet.getBalance().amount(), wallet.getReservedBalance().amount(),
                wallet.getCurrency().getCurrencyCode(), wallet.getStatus().name());
    }

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
}
