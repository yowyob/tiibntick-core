package com.yowyob.tiibntick.core.sync.domain.model;

import com.yowyob.tiibntick.core.sync.domain.model.enums.ConflictResolution;

import java.time.LocalDateTime;
import java.util.Objects;

public record ConflictRecord(
        String aggregateType,
        String aggregateId,
        String clientValue,
        String serverValue,
        LocalDateTime clientTimestamp,
        LocalDateTime serverTimestamp,
        ConflictResolution resolution,
        String resolvedValue
) {
    public ConflictRecord {
        Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(resolution);
    }

    public boolean isServerWins() {
        return resolution == ConflictResolution.SERVER_WINS;
    }

    public boolean isClientWins() {
        return resolution == ConflictResolution.CLIENT_WINS;
    }

    public boolean requiresManualMerge() {
        return resolution == ConflictResolution.MANUAL_MERGE;
    }

    @Override
    public String toString() {
        return "ConflictRecord{aggregate=" + aggregateType + "/" + aggregateId + ", resolution=" + resolution + "}";
    }
}
