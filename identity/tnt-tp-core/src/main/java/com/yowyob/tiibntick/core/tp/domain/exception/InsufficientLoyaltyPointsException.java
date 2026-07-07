package com.yowyob.tiibntick.core.tp.domain.exception;

import java.util.UUID;

/**
 * Thrown when a loyalty points redemption request exceeds the available balance.
 *
 * @author MANFOUO Braun
 */
public class InsufficientLoyaltyPointsException extends RuntimeException {

    private final UUID thirdPartyId;
    private final int requested;
    private final int available;

    public InsufficientLoyaltyPointsException(UUID thirdPartyId, int requested, int available) {
        super(String.format(
                "Insufficient loyalty points for thirdParty=%s: requested=%d, available=%d",
                thirdPartyId, requested, available));
        this.thirdPartyId = thirdPartyId;
        this.requested = requested;
        this.available = available;
    }

    public UUID getThirdPartyId() { return thirdPartyId; }
    public int getRequested() { return requested; }
    public int getAvailable() { return available; }
}
