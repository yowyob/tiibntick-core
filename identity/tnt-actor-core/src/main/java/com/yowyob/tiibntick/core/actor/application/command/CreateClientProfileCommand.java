package com.yowyob.tiibntick.core.actor.application.command;

import java.util.UUID;

public record CreateClientProfileCommand(
        UUID tenantId,
        UUID actorId) {
}
