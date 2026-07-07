package com.yowyob.tiibntick.core.billing.invoice.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)

/**
 * Thrown when an invoice is not found by its identifier.
 *
 * @author MANFOUO Braun
 */
public class InvoiceNotFoundException extends RuntimeException {

    public InvoiceNotFoundException(UUID invoiceId) {
        super("Invoice not found: " + invoiceId);
    }

    public InvoiceNotFoundException(String invoiceNumber) {
        super("Invoice not found for number: " + invoiceNumber);
    }
}
