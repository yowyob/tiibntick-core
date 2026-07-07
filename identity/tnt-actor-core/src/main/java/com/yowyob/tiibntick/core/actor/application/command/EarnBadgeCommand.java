package com.yowyob.tiibntick.core.actor.application.command;

import com.yowyob.tiibntick.core.actor.domain.model.ActorType;

import java.util.UUID;

public record EarnBadgeCommand(
        UUID tenantId,
        UUID actorId,
        ActorType actorType,
        String badgeCode,
        String badgeLabel) {
}
