package com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.web.dto;

import com.yowyob.tiibntick.core.agency.org.hubops.domain.HubParcelRecord;

import java.time.Instant;
import java.util.UUID;

public record HubParcelResponse(
        UUID id,
        UUID hubId,
        UUID missionId,
        String trackingCode,
        Instant depositedAt,
        Instant withdrawalDeadline,
        String status,
        UUID coreHubPackageEntryId
) {
    public static HubParcelResponse from(HubParcelRecord r) {
        return new HubParcelResponse(
                r.getId(), r.getHubId(), r.getMissionId(), r.getTrackingCode(),
                r.getDepositedAt(), r.getWithdrawalDeadline(),
                r.getStatus().name(), r.getCoreHubPackageEntryId());
    }
}
