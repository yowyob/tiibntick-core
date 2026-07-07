package com.yowyob.tiibntick.core.billing.invoice.application.port.in;

import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.*;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.Invoice;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use case: Generate a new invoice and immediately issue it.
 *
 * @author MANFOUO Braun
 */
/* public interface GenerateInvoiceUseCase {
    Mono<Invoice> generate(GenerateInvoiceCommand command);
} */

/**
 * Use case: Retrieve a single invoice by its UUID.
 */
interface GetInvoiceUseCase {
    Mono<Invoice> getById(UUID invoiceId);
    Mono<Invoice> getByNumber(String invoiceNumber);
    Flux<Invoice> listByMissionId(String missionId);
    Flux<Invoice> listByClientId(UUID tenantId, String clientId);
}

/**
 * Use case: Cancel an invoice.
 */
interface CancelInvoiceUseCase {
    Mono<Invoice> cancel(CancelInvoiceCommand command);
}

/**
 * Use case: Record payment and transition to PAID.
 */
interface MarkInvoicePaidUseCase {
    Mono<Invoice> markPaid(MarkInvoicePaidCommand command);
}

/**
 * Use case: Generate or retrieve the PDF for an invoice.
 */
interface GenerateInvoicePdfUseCase {
    Mono<String> generatePdf(UUID invoiceId);
}

/**
 * Use case: Mark overdue invoices (called by scheduler).
 */
interface MarkOverdueInvoicesUseCase {
    Mono<Long> markOverdueInvoices();
}
