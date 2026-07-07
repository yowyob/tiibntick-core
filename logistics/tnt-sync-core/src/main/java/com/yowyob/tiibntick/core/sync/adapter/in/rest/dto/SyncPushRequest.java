package com.yowyob.tiibntick.core.sync.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SyncPushRequest(
        String lastSyncToken,
        List<OfflineOpDto> operations
) {
    public boolean hasOperations() {
        return operations != null && !operations.isEmpty();
    }
}
