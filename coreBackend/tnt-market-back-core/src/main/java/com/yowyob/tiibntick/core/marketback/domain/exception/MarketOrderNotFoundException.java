package com.yowyob.tiibntick.core.marketback.domain.exception;

/** Thrown when a MarketOrder is not found. @author MANFOUO Braun */
public class MarketOrderNotFoundException extends MarketDomainException {
    public MarketOrderNotFoundException(String id) {
        super("MarketOrder not found: " + id);
    }
}
