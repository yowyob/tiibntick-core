package com.yowyob.tiibntick.core.sync.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOpId;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpType;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OfflineOpDto(
        String id,
        String type,
        String aggregateType,
        String aggregateId,
        String payload,
        long localTimestampMs,
        long sequenceNumber
) {
    public OfflineOperation toDomain(String userId, String tenantId, String deviceId) {
        return new OfflineOperation(
                id != null ? OfflineOpId.of(id) : OfflineOpId.generate(),
                userId, tenantId, deviceId,
                OfflineOpType.valueOf(type.toUpperCase()),
                aggregateType, aggregateId, payload,
                java.time.Instant.ofEpochMilli(localTimestampMs)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(),
                sequenceNumber
        );
    }
}
