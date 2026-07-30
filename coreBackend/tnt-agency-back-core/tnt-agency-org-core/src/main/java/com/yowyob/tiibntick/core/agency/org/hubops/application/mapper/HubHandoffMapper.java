package com.yowyob.tiibntick.core.agency.org.hubops.application.mapper;

import com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.web.dto.HubHandoffResponse;
import com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence.entity.HubHandoffRequestEntity;

public final class HubHandoffMapper {

    private HubHandoffMapper() {}

    public static HubHandoffResponse toResponse(HubHandoffRequestEntity e) {
        return new HubHandoffResponse(
                e.getId(), e.getTenantId(), e.getAgencyId(), e.getHubId(),
                e.getHandoffType(), e.getStatus(),
                e.getMissionId(), e.getPackageId(), e.getTrackingCode(),
                e.getRequesterActorId(), e.getRequesterRole(), e.getRequesterLabel(),
                e.getWithdrawParty(),
                e.getValidatedByActorId(), e.getValidatedByLabel(),
                e.getNotes(), e.getCreatedAt(), e.getValidatedAt(), e.getCompletedAt());
    }
}
