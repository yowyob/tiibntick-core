package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import java.time.Instant;
import java.util.UUID;

public record TrustLinkResponse(
        UUID id,
        UUID fromNodeId,
        UUID toNodeId,
        Instant createdAt
) {
}
