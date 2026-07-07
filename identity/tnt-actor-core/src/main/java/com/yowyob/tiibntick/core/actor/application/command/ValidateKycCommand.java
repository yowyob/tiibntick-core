package com.yowyob.tiibntick.core.actor.application.command;

import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.actor.domain.model.KycStatus;

import java.util.UUID;

public record ValidateKycCommand(
        UUID tenantId,
        UUID actorId,
        ActorType actorType,
        KycStatus newKycStatus,
        String validatedBy,
        String rejectionReason) {
}
