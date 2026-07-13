package com.yowyob.tiibntick.core.agency.assignment.application.mapper;

import com.yowyob.tiibntick.core.agency.assignment.adapter.out.persistence.entity.AgencyMissionEntity;
import com.yowyob.tiibntick.core.agency.assignment.domain.AgencyMission;
import com.yowyob.tiibntick.core.agency.assignment.domain.vo.MissionStatus;

public final class MissionMapper {

    private MissionMapper() {}

    public static AgencyMission toDomain(AgencyMissionEntity e) {
        return new AgencyMission(
                e.getId(), e.getTenantId(), e.getAgencyId(), e.getCoreMissionId(),
                e.getAssignedDelivererId(), e.getAssignedVehicleId(),
                MissionStatus.valueOf(e.getStatus()),
                e.getScheduledAt(), e.getStartedAt(), e.getCompletedAt(),
                e.getCancelledAt(), e.getCancellationReason(),
                e.getQuotedAmount(), e.getQuotedCurrency(),
                e.getBranchId(), e.getPickupAddress(), e.getDeliveryAddress(),
                e.getSenderName(), e.getRecipientName(), e.getRecipientPhone(),
                e.getWeightKg(), e.getDistanceKm(), e.getPackagesCount(),
                e.getPriority(), e.getTargetHubId(),
                e.getCreatedAt(), e.getUpdatedAt(),
                e.getVersion() != null ? e.getVersion() : 0L);
    }

    public static AgencyMissionEntity toEntity(AgencyMission m) {
        AgencyMissionEntity e = new AgencyMissionEntity();
        e.setId(m.getId());
        e.setTenantId(m.getTenantId());
        e.setAgencyId(m.getAgencyId());
        e.setCoreMissionId(m.getCoreMissionId());
        e.setAssignedDelivererId(m.getAssignedDelivererId());
        e.setAssignedVehicleId(m.getAssignedVehicleId());
        e.setStatus(m.getStatus().name());
        e.setScheduledAt(m.getScheduledAt());
        e.setStartedAt(m.getStartedAt());
        e.setCompletedAt(m.getCompletedAt());
        e.setCancelledAt(m.getCancelledAt());
        e.setCancellationReason(m.getCancellationReason());
        e.setQuotedAmount(m.getQuotedAmount());
        e.setQuotedCurrency(m.getQuotedCurrency());
        e.setBranchId(m.getBranchId());
        e.setPickupAddress(m.getPickupAddress());
        e.setDeliveryAddress(m.getDeliveryAddress());
        e.setSenderName(m.getSenderName());
        e.setRecipientName(m.getRecipientName());
        e.setRecipientPhone(m.getRecipientPhone());
        e.setWeightKg(m.getWeightKg());
        e.setDistanceKm(m.getDistanceKm());
        e.setPackagesCount(m.getPackagesCount());
        e.setPriority(m.getPriority());
        e.setTargetHubId(m.getTargetHubId());
        e.setCreatedAt(m.getCreatedAt());
        e.setUpdatedAt(m.getUpdatedAt());
        e.setVersion(m.getVersion());
        return e;
    }
}
