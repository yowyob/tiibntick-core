package com.yowyob.tiibntick.core.sync.domain.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record SyncDelta(
        String tenantId,
        String userId,
        SyncToken sinceToken,
        List<DeltaRecord> records,
        List<ConflictRecord> conflicts,
        SyncToken newToken,
        LocalDateTime generatedAt
) {
    public SyncDelta {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(newToken);
        Objects.requireNonNull(generatedAt);
        records = records != null ? Collections.unmodifiableList(records) : Collections.emptyList();
        conflicts = conflicts != null ? Collections.unmodifiableList(conflicts) : Collections.emptyList();
    }

    public static SyncDelta empty(String tenantId, String userId, SyncToken since, SyncToken next) {
        return new SyncDelta(tenantId, userId, since, Collections.emptyList(), Collections.emptyList(), next, LocalDateTime.now());
    }

    public boolean isEmpty() {
        return records.isEmpty();
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    public int recordCount() {
        return records.size();
    }

    public int conflictCount() {
        return conflicts.size();
    }

    @Override
    public String toString() {
        return "SyncDelta{tenant=" + tenantId + ", records=" + records.size() + ", conflicts=" + conflicts.size() + "}";
    }
}
