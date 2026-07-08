package com.yowyob.tiibntick.core.accounting.adapter.out.kernel;

import com.yowyob.tiibntick.common.kernel.KernelResponses;
import com.yowyob.tiibntick.core.accounting.application.port.out.KernelAccountingPort;
import com.yowyob.tiibntick.core.accounting.domain.model.KernelInvoiceDto;
import com.yowyob.tiibntick.core.accounting.domain.model.KernelJournalEntryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter — Kernel Accounting Bridge via reactive HTTP (WebClient).
 *
 * <p>Implements {@link KernelAccountingPort} by calling the Yowyob Kernel REST API
 * (RT-comops-accounting-core). All calls are non-blocking (Reactor Mono).</p>
 *
 * <p>Design contract:
 * <ul>
 *   <li>HTTP 404 from Kernel → {@code Mono.empty()} — Kernel link is optional in TNT.</li>
 *   <li>Network or timeout errors → {@code Mono.empty()} with a WARN log, allowing the
 *       accounting service to continue posting entries without the Kernel link.</li>
 * </ul>
 * </p>
 *
 * <p>WebClient bean provided by
 * {@link com.yowyob.tiibntick.common.config.KernelWebClientConfig}.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class KernelAccountingAdapter implements KernelAccountingPort {

    private static final Logger log = LoggerFactory.getLogger(KernelAccountingAdapter.class);

    private final WebClient kernelWebClient;

    /**
     * @param kernelWebClient reactive WebClient pre-configured with the Kernel base URL.
     *                        Qualified as "kernelWebClient" to avoid bean conflicts.
     */
    public KernelAccountingAdapter(@Qualifier("kernelWebClient") WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    /**
     * {@inheritDoc}
     *
     * <p>GET /api/accounting/invoices/{kernelInvoiceId}. Unused today (no caller) but kept —
     * this one has a real backing Kernel resource, unlike {@link #findJournalEntryById}.</p>
     */
    @Override
    public Mono<KernelInvoiceDto> findInvoiceById(UUID kernelInvoiceId) {
        var responseSpec = kernelWebClient.get()
                .uri("/api/accounting/invoices/{id}", kernelInvoiceId)
                .retrieve();
        return KernelResponses.unwrapObject(responseSpec, KernelInvoiceDto.class, log,
                "findInvoiceById " + kernelInvoiceId);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Known gap</b> (see ADR-013 / known-issues.md #13): the Kernel has no
     * "journal entry" resource shaped like TiiBnTick's double-entry model (separate
     * debit/credit totals, entry type, posted-at). The closest candidate,
     * {@code GET /api/accounting/operations/{operationId}} → {@code AccountingOperationView},
     * models a single-amount operation, not a double-entry posting — mapping to it would be
     * a guess, not a fix. Deliberately left calling a non-existent path (unused today, zero
     * callers) rather than silently binding to the wrong resource shape.</p>
     */
    @Override
    public Mono<KernelJournalEntryDto> findJournalEntryById(UUID kernelJournalEntryId) {
        return kernelWebClient.get()
                .uri("/accounting/journal-entries/{id}", kernelJournalEntryId)
                .retrieve()
                .bodyToMono(KernelJournalEntryDto.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    log.debug("Kernel journal entry not found: {}", kernelJournalEntryId);
                    return Mono.empty();
                })
                .onErrorResume(Exception.class, ex -> {
                    log.warn("Kernel accounting bridge error for journalEntryId={}: {}",
                            kernelJournalEntryId, ex.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Known gap</b> (see ADR-013): {@code GET /api/accounting/invoices} only accepts
     * a Kernel {@code organizationId} — there is no {@code tenantId}/{@code referenceId}
     * filter. This module has no {@code tenantId}→Kernel-{@code organizationId} resolution
     * (same class of gap as {@code tnt-inventory-core}'s warehouse→agency mapping, see
     * ADR-011). Path prefix corrected to a real resource; query params left as-is pending
     * that resolution — always resolves empty (fail-open), same net behavior as before.</p>
     */
    @Override
    public Mono<KernelInvoiceDto> findInvoiceByReferenceId(UUID tenantId, String referenceId) {
        return kernelWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/accounting/invoices")
                        .queryParam("tenantId", tenantId)
                        .queryParam("referenceId", referenceId)
                        .build())
                .retrieve()
                .bodyToMono(KernelInvoiceDto.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    log.debug("No Kernel invoice for referenceId={} tenant={}", referenceId, tenantId);
                    return Mono.empty();
                })
                .onErrorResume(Exception.class, ex -> {
                    log.warn("Kernel accounting bridge error for referenceId={}: {}",
                            referenceId, ex.getMessage());
                    return Mono.empty();
                });
    }
}
