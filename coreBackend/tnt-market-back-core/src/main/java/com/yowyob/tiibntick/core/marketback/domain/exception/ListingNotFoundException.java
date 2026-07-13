package com.yowyob.tiibntick.core.marketback.domain.exception;

/** Thrown when a MarketListing is not found. @author MANFOUO Braun */
public class ListingNotFoundException extends MarketDomainException {
    public ListingNotFoundException(String id) {
        super("MarketListing not found: " + id);
    }
}
