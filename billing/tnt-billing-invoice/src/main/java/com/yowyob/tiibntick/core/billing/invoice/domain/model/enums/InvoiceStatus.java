package com.yowyob.tiibntick.core.billing.invoice.domain.model.enums;

/**
 * Lifecycle states of an Invoice in TiiBnTick.
 *
 * <p>Transition diagram:
 * DRAFT → ISSUED → PARTIALLY_PAID → PAID
 *                ↘ OVERDUE → CANCELLED
 *                ↘ CREDITED
 * </p>
 *
 * @author MANFOUO Braun
 */
public enum InvoiceStatus {
    /** Invoice created but not yet sent to client. */
    DRAFT,
    /** Invoice issued and sent to client, awaiting payment. */
    ISSUED,
    /** Partial payment received. */
    PARTIALLY_PAID,
    /** Fully paid. */
    PAID,
    /** Past due date and still unpaid. */
    OVERDUE,
    /** Cancelled (before payment or as refund). */
    CANCELLED,
    /** Replaced by a credit note. */
    CREDITED
}
