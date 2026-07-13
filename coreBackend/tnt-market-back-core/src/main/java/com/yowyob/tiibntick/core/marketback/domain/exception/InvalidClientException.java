package com.yowyob.tiibntick.core.marketback.domain.exception;

/**
 * Thrown when a MarketOrder is placed for a clientId that does not resolve to a
 * real actor in the Kernel (tnt-actor-core's {@code IKernelActorPort#exists}).
 *
 * @author MANFOUO Braun
 */
public class InvalidClientException extends MarketDomainException {
    public InvalidClientException(String clientId) {
        super("Invalid client: no actor found in Kernel for clientId=" + clientId);
    }
}
