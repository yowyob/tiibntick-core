package com.yowyob.tiibntick.core.actor.application.command;

import java.util.UUID;

public record AssociateFreelancerCommand(
        UUID tenantId,
        UUID freelancerActorId,
        UUID agencyId) {
}
