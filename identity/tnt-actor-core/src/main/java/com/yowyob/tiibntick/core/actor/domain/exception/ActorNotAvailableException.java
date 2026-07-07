package com.yowyob.tiibntick.core.actor.domain.exception;

import java.util.UUID;

public class ActorNotAvailableException extends RuntimeException {

    public ActorNotAvailableException(UUID actorId, String status) {
        super("Actor " + actorId + " is not available. Current status: " + status);
    }
}
