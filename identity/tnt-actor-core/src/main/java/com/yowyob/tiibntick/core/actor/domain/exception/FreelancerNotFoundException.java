package com.yowyob.tiibntick.core.actor.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)

public class FreelancerNotFoundException extends RuntimeException {

    public FreelancerNotFoundException(UUID actorId) {
        super("Freelancer profile not found for actor: " + actorId);
    }

    public FreelancerNotFoundException(UUID tenantId, UUID actorId) {
        super("Freelancer profile not found for actor: " + actorId + " in tenant: " + tenantId);
    }
}
