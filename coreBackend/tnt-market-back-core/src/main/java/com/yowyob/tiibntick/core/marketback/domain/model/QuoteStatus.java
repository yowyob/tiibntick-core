package com.yowyob.tiibntick.core.marketback.domain.model;

/** Status of a QuoteRequest (demande de devis). @author MANFOUO Braun */
public enum QuoteStatus {
    PENDING,
    RESPONDED,
    SELECTED,
    CONVERTED_TO_ORDER,
    EXPIRED,
    CANCELLED
}
