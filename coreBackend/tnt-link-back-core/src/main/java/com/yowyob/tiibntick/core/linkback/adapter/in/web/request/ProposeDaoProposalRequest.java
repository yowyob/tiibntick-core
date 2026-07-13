package com.yowyob.tiibntick.core.linkback.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ProposeDaoProposalRequest(
        @NotBlank String title,
        String description,
        @NotNull Instant votingDeadline
) {
}
