package com.yowyob.tiibntick.core.accounting.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root representing a double-entry accounting journal entry.
 *
 * <p>A valid (balanced) entry satisfies: {@code sum(debitAmounts) == sum(creditAmounts)}.
 * Lifecycle: DRAFT → VALIDATED → POSTED (terminal state).</p>
 *
 * <p><b>Kernel integration:</b> Two optional logical references to the Yowyob Kernel
 * (RT-comops-accounting-core) are maintained on each entry:
 * <ul>
 *   <li>{@code kernelInvoiceId} — UUID of the Kernel invoice that triggered this entry
 *       (e.g. when auto-generated from {@code tnt.billing.invoice.paid}). {@code null}
 *       for entries not linked to a Kernel invoice (payroll, adjustments, etc.).</li>
 *   <li>{@code kernelJournalEntryId} — UUID of the counterpart entry in the Kernel ERP,
 *       when the entry has been synced to the Kernel. {@code null} until sync occurs.</li>
 * </ul>
 * There is <em>no Java inheritance</em> from Kernel accounting classes — both links are
 * UUID references stored in {@code accounting.journal_entries}.</p>
 *
 * @author MANFOUO Braun
 */
public final class JournalEntry {

    private final UUID id;
    private final UUID tenantId;
    private final UUID organizationId;
    private final JournalNumber number;
    private final JournalType type;
    private final String referenceType;
    private final String referenceId;
    private final List<JournalEntryLine> lines;
    private final String description;
    private final JournalStatus status;
    private final String createdByUserId;
    private final Instant postedAt;

    /**
     * Optional logical reference to the Kernel invoice (RT-comops-accounting-core).
     * {@code null} for entries not generated from a Kernel invoice event.
     */
    private final UUID kernelInvoiceId;

    /**
     * Optional logical reference to the counterpart Kernel journal entry.
     * {@code null} until the entry is synced to the Kernel ERP.
     */
    private final UUID kernelJournalEntryId;

    private final Instant createdAt;
    private final Instant updatedAt;

    private JournalEntry(UUID id, UUID tenantId, UUID organizationId, JournalNumber number,
                         JournalType type, String referenceType, String referenceId,
                         List<JournalEntryLine> lines, String description, JournalStatus status,
                         String createdByUserId, Instant postedAt,
                         UUID kernelInvoiceId, UUID kernelJournalEntryId,
                         Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.organizationId = Objects.requireNonNull(organizationId);
        this.number = Objects.requireNonNull(number);
        this.type = Objects.requireNonNull(type);
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.lines = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(lines)));
        this.description = description;
        this.status = Objects.requireNonNull(status);
        this.createdByUserId = createdByUserId;
        this.postedAt = postedAt;
        this.kernelInvoiceId = kernelInvoiceId;       // nullable — optional Kernel invoice link
        this.kernelJournalEntryId = kernelJournalEntryId; // nullable — optional Kernel JE link
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    // ─── Factories ────────────────────────────────────────────────────────────

    /**
     * Creates a new JournalEntry in DRAFT status with no Kernel links.
     */
    public static JournalEntry create(UUID tenantId, UUID organizationId, JournalNumber number,
                                       JournalType type, String referenceType, String referenceId,
                                       List<JournalEntryLine> lines, String description,
                                       String createdByUserId) {
        Instant now = Instant.now();
        return new JournalEntry(UUID.randomUUID(), tenantId, organizationId, number, type,
                referenceType, referenceId, lines, description, JournalStatus.DRAFT,
                createdByUserId, null, null, null, now, now);
    }

    /**
     * Creates a new JournalEntry in DRAFT status with an optional Kernel invoice link.
     *
     * @param kernelInvoiceId optional UUID of the Kernel invoice that triggered this entry
     */
    public static JournalEntry createWithKernelInvoice(UUID tenantId, UUID organizationId,
                                                        JournalNumber number, JournalType type,
                                                        String referenceType, String referenceId,
                                                        List<JournalEntryLine> lines, String description,
                                                        String createdByUserId, UUID kernelInvoiceId) {
        Instant now = Instant.now();
        return new JournalEntry(UUID.randomUUID(), tenantId, organizationId, number, type,
                referenceType, referenceId, lines, description, JournalStatus.DRAFT,
                createdByUserId, null, kernelInvoiceId, null, now, now);
    }

    /**
     * Rehydrates a JournalEntry from its persisted state (R2DBC mapping).
     */
    public static JournalEntry rehydrate(UUID id, UUID tenantId, UUID organizationId,
                                          JournalNumber number, JournalType type,
                                          String referenceType, String referenceId,
                                          List<JournalEntryLine> lines, String description,
                                          JournalStatus status, String createdByUserId,
                                          Instant postedAt, UUID kernelInvoiceId,
                                          UUID kernelJournalEntryId,
                                          Instant createdAt, Instant updatedAt) {
        return new JournalEntry(id, tenantId, organizationId, number, type, referenceType,
                referenceId, lines, description, status, createdByUserId, postedAt,
                kernelInvoiceId, kernelJournalEntryId, createdAt, updatedAt);
    }

    // ─── State transitions ────────────────────────────────────────────────────

    /** DRAFT → VALIDATED (checks balance invariant). */
    public JournalEntry validate() {
        if (status != JournalStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT entries can be validated");
        }
        if (!isBalanced()) {
            throw new IllegalStateException(
                    String.format("Entry is not balanced: debits=%s, credits=%s",
                            debitTotal(), creditTotal()));
        }
        return new JournalEntry(id, tenantId, organizationId, number, type, referenceType,
                referenceId, lines, description, JournalStatus.VALIDATED, createdByUserId,
                null, kernelInvoiceId, kernelJournalEntryId, createdAt, Instant.now());
    }

    /** VALIDATED → POSTED (immutable, ledger entry). */
    public JournalEntry post() {
        if (status != JournalStatus.VALIDATED) {
            throw new IllegalStateException("Only VALIDATED entries can be posted");
        }
        Instant now = Instant.now();
        return new JournalEntry(id, tenantId, organizationId, number, type, referenceType,
                referenceId, lines, description, JournalStatus.POSTED, createdByUserId,
                now, kernelInvoiceId, kernelJournalEntryId, createdAt, now);
    }

    /** DRAFT → CANCELLED. */
    public JournalEntry cancel() {
        if (status != JournalStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT entries can be cancelled");
        }
        return new JournalEntry(id, tenantId, organizationId, number, type, referenceType,
                referenceId, lines, description, JournalStatus.CANCELLED, createdByUserId,
                null, kernelInvoiceId, kernelJournalEntryId, createdAt, Instant.now());
    }

    /** Adds a line to a DRAFT entry (immutable — returns new instance). */
    public JournalEntry addLine(JournalEntryLine line) {
        if (status != JournalStatus.DRAFT) {
            throw new IllegalStateException("Lines can only be added to DRAFT entries");
        }
        List<JournalEntryLine> newLines = new ArrayList<>(lines);
        newLines.add(line);
        return new JournalEntry(id, tenantId, organizationId, number, type, referenceType,
                referenceId, newLines, description, status, createdByUserId, null,
                kernelInvoiceId, kernelJournalEntryId, createdAt, Instant.now());
    }

    /**
     * Links this entry to a Kernel journal entry counterpart.
     * Called after a successful sync to the Kernel ERP.
     *
     * @param newKernelJournalEntryId Kernel UUID to link
     * @return new instance with the Kernel journal entry link set
     */
    public JournalEntry withKernelJournalEntryId(UUID newKernelJournalEntryId) {
        return new JournalEntry(id, tenantId, organizationId, number, type, referenceType,
                referenceId, lines, description, status, createdByUserId, postedAt,
                kernelInvoiceId, newKernelJournalEntryId, createdAt, Instant.now());
    }

    /**
     * Links this entry to a Kernel invoice.
     *
     * @param newKernelInvoiceId Kernel invoice UUID to link
     * @return new instance with the Kernel invoice link set
     */
    public JournalEntry withKernelInvoiceId(UUID newKernelInvoiceId) {
        return new JournalEntry(id, tenantId, organizationId, number, type, referenceType,
                referenceId, lines, description, status, createdByUserId, postedAt,
                newKernelInvoiceId, kernelJournalEntryId, createdAt, Instant.now());
    }

    // ─── Query helpers ────────────────────────────────────────────────────────

    public boolean isBalanced() {
        return debitTotal().compareTo(creditTotal()) == 0;
    }

    public BigDecimal debitTotal() {
        return lines.stream().map(JournalEntryLine::debitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal creditTotal() {
        return lines.stream().map(JournalEntryLine::creditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Returns {@code true} if this entry is linked to a Kernel invoice. */
    public boolean hasKernelInvoiceLink() { return kernelInvoiceId != null; }

    /** Returns {@code true} if this entry has a Kernel journal entry counterpart. */
    public boolean hasKernelJournalEntryLink() { return kernelJournalEntryId != null; }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getOrganizationId() { return organizationId; }
    public JournalNumber getNumber() { return number; }
    public JournalType getType() { return type; }
    public String getReferenceType() { return referenceType; }
    public String getReferenceId() { return referenceId; }
    public List<JournalEntryLine> getLines() { return lines; }
    public String getDescription() { return description; }
    public JournalStatus getStatus() { return status; }
    public String getCreatedByUserId() { return createdByUserId; }
    public Instant getPostedAt() { return postedAt; }
    /** Optional Kernel invoice reference — may be {@code null}. */
    public UUID getKernelInvoiceId() { return kernelInvoiceId; }
    /** Optional Kernel journal entry reference — may be {@code null}. */
    public UUID getKernelJournalEntryId() { return kernelJournalEntryId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
