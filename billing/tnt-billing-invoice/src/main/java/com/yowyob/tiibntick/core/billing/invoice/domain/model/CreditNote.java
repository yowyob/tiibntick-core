package com.yowyob.tiibntick.core.billing.invoice.domain.model;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.CreditNoteStatus;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root: CreditNote.
 * Issued when an invoice is cancelled after payment (refund document).
 *
 * @author MANFOUO Braun
 */
public final class CreditNote {

    private final UUID id;
    private final UUID originalInvoiceId;
    private final UUID tenantId;
    private final Money amount;
    private final String reason;
    private final CreditNoteStatus status;
    private final LocalDateTime issuedAt;
    private final LocalDateTime appliedAt;

    private CreditNote(
            UUID id, UUID originalInvoiceId, UUID tenantId,
            Money amount, String reason, CreditNoteStatus status,
            LocalDateTime issuedAt, LocalDateTime appliedAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.originalInvoiceId = Objects.requireNonNull(originalInvoiceId, "originalInvoiceId is required");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.amount = Objects.requireNonNull(amount, "amount is required");
        this.reason = Objects.requireNonNull(reason, "reason is required");
        this.status = status != null ? status : CreditNoteStatus.ISSUED;
        this.issuedAt = issuedAt;
        this.appliedAt = appliedAt;
    }

    public static CreditNote issue(UUID originalInvoiceId, UUID tenantId, Money amount, String reason) {
        return new CreditNote(UUID.randomUUID(), originalInvoiceId, tenantId,
                amount, reason, CreditNoteStatus.ISSUED, LocalDateTime.now(), null);
    }

    public static CreditNote reconstitute(UUID id, UUID originalInvoiceId, UUID tenantId,
            Money amount, String reason, CreditNoteStatus status,
            LocalDateTime issuedAt, LocalDateTime appliedAt) {
        return new CreditNote(id, originalInvoiceId, tenantId, amount, reason, status, issuedAt, appliedAt);
    }

    public CreditNote apply() {
        if (status != CreditNoteStatus.ISSUED) {
            throw new IllegalStateException("CreditNote " + id + " cannot be applied in state: " + status);
        }
        return new CreditNote(id, originalInvoiceId, tenantId, amount, reason,
                CreditNoteStatus.APPLIED, issuedAt, LocalDateTime.now());
    }

    public UUID getId() { return id; }
    public UUID getOriginalInvoiceId() { return originalInvoiceId; }
    public UUID getTenantId() { return tenantId; }
    public Money getAmount() { return amount; }
    public String getReason() { return reason; }
    public CreditNoteStatus getStatus() { return status; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public LocalDateTime getAppliedAt() { return appliedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CreditNote that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
