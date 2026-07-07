package com.yowyob.tiibntick.core.actor.domain.exception;

import java.util.UUID;

public class ActorNotFoundException extends RuntimeException {

    public ActorNotFoundException(UUID actorId) {
        super("Actor not found with id: " + actorId);
    }

    public ActorNotFoundException(UUID tenantId, UUID actorId) {
        super("Actor not found with id: " + actorId + " in tenant: " + tenantId);
    }
}
