package com.yowyob.tiibntick.core.linkback.application.port.in.command;

import java.time.Instant;
import java.util.UUID;

public record ProposeDaoProposalCommand(
        UUID zoneId,
        UUID tenantId,
        String title,
        String description,
        UUID proposerId,
        Instant votingDeadline
) {
}
