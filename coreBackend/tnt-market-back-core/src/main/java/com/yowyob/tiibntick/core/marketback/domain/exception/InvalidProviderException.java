package com.yowyob.tiibntick.core.marketback.domain.exception;

/**
 * Thrown when a MarketListing references a {@code providerId} that does not exist
 * as a Kernel actor (see {@code tnt-actor-core}'s {@code IKernelActorPort}).
 *
 * @author MANFOUO Braun
 */
public class InvalidProviderException extends MarketDomainException {
    public InvalidProviderException(String providerId) {
        super("Provider actor not found in Kernel: " + providerId);
    }
}
