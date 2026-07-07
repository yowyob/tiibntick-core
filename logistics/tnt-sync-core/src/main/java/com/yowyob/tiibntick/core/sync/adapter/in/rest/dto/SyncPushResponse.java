package com.yowyob.tiibntick.core.sync.adapter.in.rest.dto;

import java.util.List;

public record SyncPushResponse(
        String sessionId,
        int operationsSubmitted,
        int operationsApplied,
        int conflictsDetected,
        int conflictsResolved,
        int discarded,
        List<ConflictDto> conflicts,
        String newSyncToken,
        String message
) {
    public record ConflictDto(
            String aggregateType,
            String aggregateId,
            String resolution,
            String resolvedValue
    ) {}
}
