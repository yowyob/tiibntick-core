package com.yowyob.tiibntick.core.actor.application.command;

import com.yowyob.tiibntick.core.actor.domain.model.ActorType;

import java.util.UUID;

public record SubmitKycCommand(
        UUID tenantId,
        UUID actorId,
        ActorType actorType,
        String documentUrl,
        String documentType) {
}
