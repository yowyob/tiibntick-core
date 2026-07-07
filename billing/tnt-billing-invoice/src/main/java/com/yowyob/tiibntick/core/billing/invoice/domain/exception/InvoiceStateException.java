package com.yowyob.tiibntick.core.billing.invoice.domain.exception;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus;

import java.util.UUID;

/**
 * Thrown when an invoice state transition is invalid.
 *
 * @author MANFOUO Braun
 */
public class InvoiceStateException extends RuntimeException {

    private final UUID invoiceId;
    private final String attemptedAction;
    private final InvoiceStatus currentStatus;

    public InvoiceStateException(UUID invoiceId, String attemptedAction, InvoiceStatus currentStatus) {
        super(String.format("Cannot '%s' invoice %s in state %s", attemptedAction, invoiceId, currentStatus));
        this.invoiceId = invoiceId;
        this.attemptedAction = attemptedAction;
        this.currentStatus = currentStatus;
    }

    public UUID getInvoiceId() { return invoiceId; }
    public String getAttemptedAction() { return attemptedAction; }
    public InvoiceStatus getCurrentStatus() { return currentStatus; }
}
