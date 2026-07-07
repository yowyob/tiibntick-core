package com.yowyob.tiibntick.core.actor.application.command;

import java.util.UUID;

public record AssignMissionCommand(
        UUID tenantId,
        UUID delivererActorId,
        UUID missionId) {
}
