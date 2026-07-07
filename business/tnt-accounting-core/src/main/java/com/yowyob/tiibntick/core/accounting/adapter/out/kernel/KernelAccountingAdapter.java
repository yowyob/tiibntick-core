package com.yowyob.tiibntick.core.accounting.adapter.out.kernel;

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
     * <p>GET /accounting/invoices/{kernelInvoiceId}</p>
     */
    @Override
    public Mono<KernelInvoiceDto> findInvoiceById(UUID kernelInvoiceId) {
        return kernelWebClient.get()
                .uri("/accounting/invoices/{id}", kernelInvoiceId)
                .retrieve()
                .bodyToMono(KernelInvoiceDto.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    log.debug("Kernel invoice not found: {}", kernelInvoiceId);
                    return Mono.empty();
                })
                .onErrorResume(Exception.class, ex -> {
                    log.warn("Kernel accounting bridge error for invoiceId={}: {}",
                            kernelInvoiceId, ex.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>GET /accounting/journal-entries/{kernelJournalEntryId}</p>
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
     * <p>GET /accounting/invoices?tenantId=...&referenceId=...</p>
     */
    @Override
    public Mono<KernelInvoiceDto> findInvoiceByReferenceId(UUID tenantId, String referenceId) {
        return kernelWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/accounting/invoices")
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
