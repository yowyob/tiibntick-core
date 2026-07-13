package com.yowyob.tiibntick.core.marketback.domain.exception;

/** Thrown when a ServiceOffer is not found. @author MANFOUO Braun */
public class ServiceOfferNotFoundException extends MarketDomainException {
    public ServiceOfferNotFoundException(String id) {
        super("ServiceOffer not found: " + id);
    }
}
