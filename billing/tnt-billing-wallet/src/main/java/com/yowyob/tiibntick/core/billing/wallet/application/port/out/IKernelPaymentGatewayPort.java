package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.dto.KernelPaymentOrderDto;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.dto.KernelWalletDto;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.dto.KernelWalletTransactionDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outbound port for the Kernel's {@code payment-controller}
 * ({@code /api/payments/wallets/**}) and {@code payment-gateway-controller}
 * ({@code /api/payments/orders/**}) — see
 * {@code docs/audits/remediation/workstream-payment-billing-kernel-delegation.md}
 * (payment/wallet delegation workstream, steps 3-5).
 *
 * <p>Covers the 7 {@code payment-controller} wallet-ledger operations plus the
 * {@code initiateOrder}/{@code refreshOrder} pair of {@code payment-gateway-controller}
 * needed to dispatch a real-world Mobile Money/card payment and reconcile its outcome
 * (steps 4/5 — the initial workstream draft wrongly concluded
 * {@code payment-gateway-controller} did not exist; corrected 2026-07-18 after direct
 * verification against {@code docs/kernel-api/endpoints.md}/{@code openapi.json}). It
 * still does <strong>not</strong> cover wallet freeze/unfreeze, balance reservation, or
 * refunds — none of these have a documented Kernel endpoint as of this workstream.
 *
 * <p>Implemented by {@code KernelPaymentGatewayAdapter}, calling the shared
 * {@code kernelPaymentWebClient} bean (defined once in {@code tnt-bootstrap}'s
 * {@code KernelBridgeConfig}) and unwrapping responses via {@code KernelResponses}
 * (never skipping the {@code {success, data, ...}} envelope — ADR-012).
 *
 * @author MANFOUO Braun
 */
public interface IKernelPaymentGatewayPort {

    /** {@code POST /api/payments/wallets} — creates a Kernel-managed wallet. */
    Mono<KernelWalletDto> createWallet(UUID organizationId, String label);

    /** {@code GET /api/payments/wallets/{walletId}} — fail-open (empty on 404/error). */
    Mono<KernelWalletDto> getWallet(UUID walletId);

    /** {@code GET /api/payments/wallets/owner/{ownerId}} — fail-open (empty on 404/error). */
    Mono<KernelWalletDto> getWalletByOwner(UUID ownerId);

    /**
     * {@code GET /api/payments/wallets/{walletId}/can-operate?amount=...} — errors propagate
     * (this is a safety gate before a debit; treating a Kernel outage as "yes" would be worse
     * than blocking the operation).
     */
    Mono<Boolean> canOperate(UUID walletId, BigDecimal amount);

    /**
     * {@code POST /api/payments/wallets/{walletId}/pay} — debits the wallet. Errors propagate
     * (never fail-open on a money movement). See {@link KernelWalletTransactionDto} for the
     * documented schema-ambiguity caveat on the request/response body.
     */
    Mono<KernelWalletTransactionDto> pay(UUID walletId, BigDecimal amount, String currency,
                                         String reference, String description);

    /**
     * {@code POST /api/payments/wallets/{walletId}/recharge} — credits the wallet. Errors
     * propagate (never fail-open on a money movement). See {@link KernelWalletTransactionDto}
     * for the documented schema-ambiguity caveat on the request/response body.
     */
    Mono<KernelWalletTransactionDto> recharge(UUID walletId, BigDecimal amount, String currency,
                                              String reference, String description);

    /** {@code GET /api/payments/wallets/{walletId}/transactions} — fail-open (empty on error). */
    Flux<KernelWalletTransactionDto> getTransactions(UUID walletId);

    /**
     * {@code POST /api/payments/orders} — initiates a real-world Mobile Money/card payment
     * order via {@code payment-gateway-controller}. Unlike {@link #pay}/{@link #recharge}
     * above (which only move the internal Kernel wallet ledger), this is what actually
     * dispatches to an external provider (MYCOOLPAY for Mobile Money, STRIPE for cards —
     * the only two values the Kernel's {@code InitiatePaymentRequest.provider}/{@code method}
     * enums accept). Errors propagate (never fail-open): a caller must not treat a failed
     * initiation as "provider dispatch happened".
     *
     * @param provider        {@code "MYCOOLPAY"} or {@code "STRIPE"}
     * @param method          {@code "MOBILE_MONEY"} or {@code "CARD"}
     * @param amount          payment amount
     * @param currency        ISO currency code
     * @param payerReference  payer's phone number (Mobile Money) or other payer identifier
     * @param description     human-readable payment description
     * @param callbackUrl     redirect/callback URL for redirect-based flows
     * @param idempotencyKey  TiiBnTick's own idempotency key for this payment attempt
     */
    Mono<KernelPaymentOrderDto> initiateOrder(String provider, String method, BigDecimal amount,
                                               String currency, String payerReference,
                                               String description, String callbackUrl,
                                               String idempotencyKey);

    /**
     * {@code POST /api/payments/orders/{id}/refresh} — actively asks the Kernel to re-check
     * an order's status against the upstream provider. Used to reconcile a pending payment
     * since the Kernel documents no outbound webhook back to TiiBnTick for
     * {@code payment-gateway-controller} orders (see workstream step 5). Fail-open (empty)
     * on error — a single failed poll attempt must not break the reconciliation loop; the
     * next scheduled tick retries.
     *
     * @param kernelOrderId the {@code id} returned by {@link #initiateOrder}
     */
    Mono<KernelPaymentOrderDto> refreshOrder(String kernelOrderId);
}
