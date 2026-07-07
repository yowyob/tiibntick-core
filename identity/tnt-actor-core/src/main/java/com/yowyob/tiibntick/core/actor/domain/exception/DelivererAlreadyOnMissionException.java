package com.yowyob.tiibntick.core.actor.domain.exception;

import java.util.UUID;

public class DelivererAlreadyOnMissionException extends RuntimeException {

    public DelivererAlreadyOnMissionException(UUID delivererActorId, UUID currentMissionId) {
        super("Deliverer " + delivererActorId + " is already on mission: " + currentMissionId);
    }
}
