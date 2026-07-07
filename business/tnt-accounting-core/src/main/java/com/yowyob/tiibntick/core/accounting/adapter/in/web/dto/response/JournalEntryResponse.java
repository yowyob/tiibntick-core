package com.yowyob.tiibntick.core.accounting.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.accounting.domain.model.JournalEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API response DTO for JournalEntry.
 * Includes optional {@code kernelInvoiceId} and {@code kernelJournalEntryId} for
 * Kernel-linked entries (may be null for informal transactions).
 *
 * @author MANFOUO Braun
 */
public record JournalEntryResponse(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        String number,
        String type,
        String referenceType,
        String referenceId,
        String description,
        String status,
        String createdByUserId,
        BigDecimal debitTotal,
        BigDecimal creditTotal,
        /** Nullable — UUID of the Kernel invoice that triggered this entry. */
        UUID kernelInvoiceId,
        /** Nullable — UUID of the counterpart entry in the Kernel ERP. */
        UUID kernelJournalEntryId,
        List<JournalEntryLineResponse> lines,
        Instant postedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static JournalEntryResponse from(JournalEntry e) {
        return new JournalEntryResponse(
                e.getId(), e.getTenantId(), e.getOrganizationId(),
                e.getNumber().value(), e.getType().name(),
                e.getReferenceType(), e.getReferenceId(), e.getDescription(),
                e.getStatus().name(), e.getCreatedByUserId(),
                e.debitTotal(), e.creditTotal(),
                e.getKernelInvoiceId(),       // nullable
                e.getKernelJournalEntryId(),  // nullable
                e.getLines().stream().map(JournalEntryLineResponse::from).toList(),
                e.getPostedAt(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
