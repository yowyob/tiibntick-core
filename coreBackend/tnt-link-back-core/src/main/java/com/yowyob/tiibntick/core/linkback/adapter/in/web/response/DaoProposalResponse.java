package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import java.time.Instant;
import java.util.UUID;

public record DaoProposalResponse(
        UUID id,
        UUID zoneId,
        String title,
        String description,
        UUID proposerId,
        String status,
        int votesFor,
        int votesAgainst,
        Instant votingDeadline,
        Instant createdAt,
        Instant updatedAt
) {
}
