package com.yowyob.tiibntick.core.billing.invoice.application.port.out;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Invoice;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Output port: persistence for Invoice aggregate.
 *
 * @author MANFOUO Braun
 */
public interface InvoiceRepository {

    Mono<Invoice> save(Invoice invoice);

    Mono<Invoice> findById(UUID invoiceId);

    Mono<Invoice> findByNumber(String invoiceNumber);

    Flux<Invoice> findByMissionId(String missionId);

    Flux<Invoice> findByClientId(UUID tenantId, String clientId);

    Flux<Invoice> findByTenantId(UUID tenantId);

    /** Finds invoices past their due date still in payable states. */
    Flux<Invoice> findOverdue(LocalDateTime now);

    Mono<Boolean> existsByMissionId(String missionId);
}
