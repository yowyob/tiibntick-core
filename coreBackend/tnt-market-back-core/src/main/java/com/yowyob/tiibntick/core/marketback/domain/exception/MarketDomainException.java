package com.yowyob.tiibntick.core.marketback.domain.exception;

/**
 * Base domain exception for TiiBnTick Market.
 * Domain-layer exceptions never import Spring or infrastructure classes.
 * @author MANFOUO Braun
 */
public class MarketDomainException extends RuntimeException {

    public MarketDomainException(String message) {
        super(message);
    }

    public MarketDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
