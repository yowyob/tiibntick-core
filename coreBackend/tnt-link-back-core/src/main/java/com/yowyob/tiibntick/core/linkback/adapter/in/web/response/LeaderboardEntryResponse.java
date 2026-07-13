package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import java.util.UUID;

public record LeaderboardEntryResponse(
        int rank,
        UUID nodeId,
        String refType,
        UUID refId,
        double trustScore,
        int points
) {
}
