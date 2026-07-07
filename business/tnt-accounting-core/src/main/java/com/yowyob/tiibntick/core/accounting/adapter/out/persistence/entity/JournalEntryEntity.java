package com.yowyob.tiibntick.core.accounting.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC entity mapped to the {@code accounting.journal_entries} table.
 *
 * <p>The {@code kernelInvoiceId} and {@code kernelJournalEntryId} columns store
 * optional logical references to the Yowyob Kernel (RT-comops-accounting-core).
 * There are no physical foreign keys cross-database.</p>
 *
 * @author MANFOUO Braun
 */
@Table(schema = "accounting", name = "journal_entries")
public record JournalEntryEntity(
        @Id @Column("id") UUID id,
        @Column("tenant_id") UUID tenantId,
        @Column("organization_id") UUID organizationId,
        @Column("number") String number,
        @Column("type") String type,
        @Column("reference_type") String referenceType,
        @Column("reference_id") String referenceId,
        @Column("description") String description,
        @Column("status") String status,
        @Column("created_by_user_id") String createdByUserId,
        @Column("posted_at") Instant postedAt,
        /**
         * Optional logical reference to the Kernel invoice (RT-comops-accounting-core).
         * NULL for entries not generated from a Kernel invoice event.
         */
        @Column("kernel_invoice_id") UUID kernelInvoiceId,
        /**
         * Optional logical reference to the counterpart Kernel journal entry.
         * NULL until the entry is synced to the Kernel ERP.
         */
        @Column("kernel_journal_entry_id") UUID kernelJournalEntryId,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt
) {}
