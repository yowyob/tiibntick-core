package com.yowyob.tiibntick.core.linkback.adapter.in.web.request;

import jakarta.validation.constraints.NotNull;

public record VoteOnProposalRequest(
        @NotNull Boolean inFavor
) {
}
