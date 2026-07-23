package com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mirrors the Kernel's {@code PaymentOrderResponse} schema
 * ({@code payment-gateway-controller}, {@code /api/payments/orders/**}).
 *
 * <p>Confirmed field-for-field against the published OpenAPI spec
 * ({@code docs/kernel-api/openapi.json}, 2026-07-18 verification — see
 * {@code docs/audits/remediation/workstream-payment-billing-kernel-delegation.md},
 * payment/wallet delegation workstream, step 4/5 correction note). Unlike
 * {@link KernelWalletTransactionDto}, there is no schema-name collision here — every field
 * below is exactly as declared on {@code PaymentOrderResponse}.
 *
 * <p>{@code id}/{@code tenantId}/{@code clientId} are kept as plain {@link String} rather
 * than {@code UUID}: the schema types them as bare {@code string} with no {@code uuid}
 * format annotation (unlike, say, the {@code id} path parameter on
 * {@code GET /api/payments/orders/{id}}, which the Kernel does document as
 * {@code string(uuid)}) — so a caller must not assume they always parse as a UUID.
 *
 * <p>{@code status} has no documented enum (same situation as
 * {@link KernelWalletTransactionDto#status}) — see {@link #isSuccess()}/{@link #isFailure()}
 * for the same best-effort, fail-toward-pending classification used there.
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelPaymentOrderDto(
        String id,
        String tenantId,
        String clientId,
        String serviceCode,
        BigDecimal amount,
        String currency,
        String provider,
        String method,
        String payerReference,
        String status,
        String providerReference,
        String redirectUrl,
        Instant createdAt,
        Instant updatedAt
) {

    private static final String SUCCESS_MARKER = "SUCCESS";
    private static final String COMPLETE_MARKER = "COMPLET";
    private static final String CONFIRM_MARKER = "CONFIRM";
    private static final String FAIL_MARKER = "FAIL";
    private static final String REJECT_MARKER = "REJECT";
    private static final String DECLINE_MARKER = "DECLINE";
    private static final String CANCEL_MARKER = "CANCEL";
    private static final String EXPIRE_MARKER = "EXPIR";

    /** Best-effort, case-insensitive classification of {@link #status} — see class javadoc. */
    public boolean isSuccess() {
        if (status == null) {
            return false;
        }
        String s = status.toUpperCase();
        return s.contains(SUCCESS_MARKER) || s.contains(COMPLETE_MARKER) || s.contains(CONFIRM_MARKER);
    }

    public boolean isFailure() {
        if (status == null) {
            return false;
        }
        String s = status.toUpperCase();
        return s.contains(FAIL_MARKER) || s.contains(REJECT_MARKER) || s.contains(DECLINE_MARKER)
                || s.contains(CANCEL_MARKER) || s.contains(EXPIRE_MARKER);
    }
}
