package com.yowyob.tiibntick.core.actor.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ClientNotFoundException extends RuntimeException {

    public ClientNotFoundException(UUID actorId) {
        super("Client profile not found for actor: " + actorId);
    }

    public ClientNotFoundException(UUID tenantId, UUID actorId) {
        super("Client profile not found for actor: " + actorId + " in tenant: " + tenantId);
    }
}
