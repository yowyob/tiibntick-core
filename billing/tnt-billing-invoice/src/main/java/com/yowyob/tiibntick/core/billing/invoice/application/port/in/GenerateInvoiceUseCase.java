package com.yowyob.tiibntick.core.billing.invoice.application.port.in;

import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.GenerateInvoiceCommand;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.Invoice;

/**
 * Use case: Generate an invoice for a completed mission.
 */
public interface GenerateInvoiceUseCase {
    Mono<Invoice> generate(GenerateInvoiceCommand command);
}