package com.yowyob.tiibntick.core.actor.application.command;

import java.util.UUID;

public record ReleaseMissionCommand(
        UUID tenantId,
        UUID delivererActorId,
        UUID missionId) {
}
