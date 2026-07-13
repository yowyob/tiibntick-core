package com.yowyob.tiibntick.core.linkback.adapter.in.web.request;

import jakarta.validation.constraints.NotNull;

public record UpdateNodeLocationRequest(
        @NotNull Double latitude,
        @NotNull Double longitude,
        Double heading,
        /** Peer count for the Proof-of-Location heuristic (see {@code NetworkNode} javadoc); 0 if omitted. */
        Integer polPeerCount
) {
}
