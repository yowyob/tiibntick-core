package com.yowyob.tiibntick.core.marketback.domain.exception;

/** Thrown when a QuoteRequest is not found. @author MANFOUO Braun */
public class QuoteRequestNotFoundException extends MarketDomainException {
    public QuoteRequestNotFoundException(String id) {
        super("QuoteRequest not found: " + id);
    }
}
