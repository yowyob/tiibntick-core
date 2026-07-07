package com.yowyob.tiibntick.core.actor.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)

public class DelivererNotFoundException extends RuntimeException {

    public DelivererNotFoundException(UUID actorId) {
        super("Deliverer profile not found for actor: " + actorId);
    }

    public DelivererNotFoundException(UUID tenantId, UUID actorId) {
        super("Deliverer profile not found for actor: " + actorId + " in tenant: " + tenantId);
    }
}
