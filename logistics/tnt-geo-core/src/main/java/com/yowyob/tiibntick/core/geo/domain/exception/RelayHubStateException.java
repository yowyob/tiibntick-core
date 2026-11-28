package com.yowyob.tiibntick.core.geo.domain.exception;

/**
 * A relay hub operation refused by the hub's approval or operating status: a transition the approval machine refuses
 * (D-138, e.g. any change on a {@code REVOKED} hub, which is definitive), or a new deposit in a hub that is not
 * approved (D-140). Mapped to HTTP 409.
 */
public class RelayHubStateException extends IllegalStateException {

    public RelayHubStateException(String message) {
        super(message);
    }
}
