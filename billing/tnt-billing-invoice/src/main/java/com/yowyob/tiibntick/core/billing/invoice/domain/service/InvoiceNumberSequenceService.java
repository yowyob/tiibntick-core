package com.yowyob.tiibntick.core.billing.invoice.domain.service;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.InvoiceNumber;
import reactor.core.publisher.Mono;

import java.time.Year;
import java.util.UUID;

/**
 * Domain service: generates unique, sequential InvoiceNumbers per tenant per year.
 *
 * <p>Format: TNT-FACT-{tenantCode}-{year}-{seq:06d}
 * Sequence is atomic and tenant-scoped via the comops-settings-core DocumentSequence.</p>
 *
 * @author MANFOUO Braun
 */
public interface InvoiceNumberSequenceService {

    /**
     * Generates the next InvoiceNumber for the given tenant in the current year.
     *
     * @param tenantId   the tenant UUID
     * @param tenantCode the short tenant code (used in the number prefix)
     * @return Mono emitting the next available InvoiceNumber
     */
    Mono<InvoiceNumber> nextNumber(UUID tenantId, String tenantCode);

    /**
     * Returns the current sequence counter for a tenant-year (for reporting purposes only).
     *
     * @param tenantId the tenant UUID
     * @param year     the year
     * @return current sequence value
     */
    Mono<Long> currentSequence(UUID tenantId, Year year);
}
