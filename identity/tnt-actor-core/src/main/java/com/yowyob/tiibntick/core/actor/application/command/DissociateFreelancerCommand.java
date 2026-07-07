package com.yowyob.tiibntick.core.actor.application.command;

import java.util.UUID;

public record DissociateFreelancerCommand(
        UUID tenantId,
        UUID freelancerActorId,
        UUID agencyId,
        String reason) {
}
