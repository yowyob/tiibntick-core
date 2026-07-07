package com.yowyob.tiibntick.core.actor.application.command;

import com.yowyob.tiibntick.core.actor.domain.model.ActorType;

import java.util.UUID;

public record RateActorCommand(
        UUID tenantId,
        UUID actorId,
        ActorType actorType,
        double score,
        UUID ratedByActorId) {
}
