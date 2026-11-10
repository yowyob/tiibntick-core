package com.yowyob.tiibntick.core.billing.invoice.application.port.in;

import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.GenerateInvoiceCommand;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.MarkInvoicePaidCommand;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.Invoice;
import reactor.core.publisher.Mono;

/**
 * Invoicing for <em>trusted in-process callers</em> that run without a user security context
 * (Kafka consumers, the delivery-payment flow after the money has moved). It is not reachable over
 * HTTP: the REST controller only exposes the permission-guarded {@code InvoiceService} methods.
 *
 * <p>Before this port existed, the Kafka consumers called the {@code @RequirePermission} methods with
 * no authentication in the reactive context, so every event-driven invoice and every event-driven
 * "mark paid" was rejected as forbidden.
 */
public interface SystemInvoicingUseCase {

    Mono<Invoice> generateAsSystem(GenerateInvoiceCommand command);

    Mono<Invoice> markPaidAsSystem(MarkInvoicePaidCommand command);

    /**
     * Issues the invoice of a mission that has just been paid, once: when the mission already has an
     * invoice, that invoice is returned (marked paid if it still is not) instead of a duplicate.
     */
    Mono<Invoice> issuePaidInvoiceAsSystem(GenerateInvoiceCommand command, String paymentRef);
}
