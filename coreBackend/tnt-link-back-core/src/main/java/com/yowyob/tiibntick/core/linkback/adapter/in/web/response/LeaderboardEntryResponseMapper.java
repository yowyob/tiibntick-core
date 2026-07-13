package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import com.yowyob.tiibntick.core.linkback.domain.model.NetworkNode;

public final class LeaderboardEntryResponseMapper {

    private LeaderboardEntryResponseMapper() {
    }

    public static LeaderboardEntryResponse toResponse(int rank, NetworkNode node) {
        return new LeaderboardEntryResponse(
                rank,
                node.getId(),
                node.getRefType().name(),
                node.getRefId(),
                node.getTrustScore(),
                node.getGamificationLevel()
        );
    }
}
