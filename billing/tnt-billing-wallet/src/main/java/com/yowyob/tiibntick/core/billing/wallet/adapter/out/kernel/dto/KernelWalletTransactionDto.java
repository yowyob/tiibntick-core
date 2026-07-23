package com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Result of a Kernel {@code pay}/{@code recharge}/{@code transactions} call
 * ({@code payment-controller}, {@code /api/payments/wallets/{walletId}/{pay,recharge,transactions}}).
 *
 * <h2>Known documentation gap — read before trusting this shape</h2>
 * <p>The Kernel's published OpenAPI spec (verified directly against
 * {@code docs/kernel-api/openapi.json}, 2026-07) references
 * {@code #/components/schemas/TransactionRequest}/{@code TransactionResponse} for these
 * three operations — but that <em>same</em> schema name is also used, with a completely
 * different (blockchain-ledger) shape, by {@code blockchain-controller},
 * {@code bank-transaction-controller} and {@code legacy-banking-controller}
 * ({@code chainCode}, {@code payloadHash}, {@code senderPublicKey}, {@code signature},
 * {@code blockId}, {@code blockHeight} — no amount/currency field at all). This is a
 * schema-name collision on the Kernel side (springdoc/swagger resolving multiple
 * distinct Java DTOs to one schema by simple class name), not a TiiBnTick-side mistake —
 * the true JSON body/response actually accepted by the wallet {@code pay}/{@code recharge}
 * endpoints cannot be verified from documentation alone.
 *
 * <p>Consequently, only {@code id} and {@code status} are mapped here — these are the
 * two fields any reasonable transaction-shaped response is virtually certain to carry
 * regardless of which underlying schema actually won the collision. Do not add more
 * fields to this DTO (e.g. a returned balance or amount) without first getting the
 * Kernel API owner (TSAFACK Savio) to confirm the real contract; see
 * {@code docs/audits/remediation/workstream-payment-billing-kernel-delegation.md} step 5.
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelWalletTransactionDto(
        UUID id,
        String status,
        Instant createdAt
) {

    private static final String SUCCESS_MARKER = "SUCCESS";
    private static final String COMPLETE_MARKER = "COMPLET";
    private static final String CONFIRM_MARKER = "CONFIRM";
    private static final String FAIL_MARKER = "FAIL";
    private static final String REJECT_MARKER = "REJECT";
    private static final String DECLINE_MARKER = "DECLINE";

    /**
     * Best-effort, case-insensitive classification of {@link #status}. Given the schema
     * uncertainty documented above, this deliberately errs toward {@code PENDING} for any
     * value it doesn't recognize — silently treating an unrecognized status as success
     * would be worse than leaving the operation pending for manual/later reconciliation.
     */
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
        return s.contains(FAIL_MARKER) || s.contains(REJECT_MARKER) || s.contains(DECLINE_MARKER);
    }
}
