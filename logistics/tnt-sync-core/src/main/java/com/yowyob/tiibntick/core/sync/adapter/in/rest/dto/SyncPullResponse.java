package com.yowyob.tiibntick.core.sync.adapter.in.rest.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SyncPullResponse(
        String newSyncToken,
        int recordCount,
        List<DeltaRecordDto> records,
        LocalDateTime generatedAt,
        boolean hasMore,
        int nextSyncRecommendedInSeconds
) {
    public record DeltaRecordDto(
            String aggregateType,
            String aggregateId,
            String operation,
            String payload,
            long serverVersion,
            LocalDateTime serverTimestamp
    ) {}
}
