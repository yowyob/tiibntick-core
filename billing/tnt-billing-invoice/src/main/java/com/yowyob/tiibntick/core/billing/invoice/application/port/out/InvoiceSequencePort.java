package com.yowyob.tiibntick.core.billing.invoice.application.port.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Output port: generates the next atomic invoice sequence number per tenant per year.
 * Backed by the comops-settings-core DocumentSequence service.
 *
 * @author MANFOUO Braun
 */
public interface InvoiceSequencePort {

    /**
     * Atomically increments and returns the next sequence value for a given tenant-year key.
     *
     * @param tenantId the tenant UUID
     * @param year     the current year (e.g., 2026)
     * @return the next sequence number (1-based)
     */
    Mono<Long> nextSequence(UUID tenantId, int year);
}
