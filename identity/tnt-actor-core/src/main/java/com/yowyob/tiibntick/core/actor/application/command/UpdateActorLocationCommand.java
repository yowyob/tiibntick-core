package com.yowyob.tiibntick.core.actor.application.command;

import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.actor.domain.model.LocationSource;

import java.util.UUID;

public record UpdateActorLocationCommand(
        UUID tenantId,
        UUID actorId,
        ActorType actorType,
        double latitude,
        double longitude,
        Double accuracy,
        LocationSource source) {
}
