package com.yowyob.tiibntick.core.agency.intake.application.mapper;

import com.yowyob.tiibntick.core.agency.intake.adapter.out.persistence.entity.ClientIntakeRequestEntity;
import com.yowyob.tiibntick.core.agency.intake.domain.ClientIntakeRequest;
import com.yowyob.tiibntick.core.agency.intake.domain.ClientIntakeRequest.DeliveryMode;
import com.yowyob.tiibntick.core.agency.intake.domain.ClientIntakeRequest.Source;
import com.yowyob.tiibntick.core.agency.intake.domain.ClientIntakeRequest.Status;

public final class IntakeMapper {

    private IntakeMapper() {}

    public static ClientIntakeRequestEntity toEntity(ClientIntakeRequest r) {
        ClientIntakeRequestEntity e = new ClientIntakeRequestEntity();
        e.setId(r.getId());
        e.setTenantId(r.getTenantId());
        e.setAgencyId(r.getAgencyId());
        e.setBranchId(r.getBranchId());
        e.setReferenceCode(r.getReferenceCode());
        e.setSource(r.getSource().name());
        e.setStatus(r.getStatus().name());
        e.setSenderName(r.getSenderName());
        e.setSenderPhone(r.getSenderPhone());
        e.setRecipientName(r.getRecipientName());
        e.setRecipientPhone(r.getRecipientPhone());
        e.setPickupAddress(r.getPickupAddress());
        e.setDeliveryAddress(r.getDeliveryAddress());
        e.setWeightKg(r.getWeightKg());
        e.setPackagesCount(r.getPackagesCount());
        e.setDeliveryMode(r.getDeliveryMode().name());
        e.setTargetHubId(r.getTargetHubId());
        e.setNotes(r.getNotes());
        e.setMissionId(r.getMissionId());
        e.setTrackingCode(r.getTrackingCode());
        e.setRejectionReason(r.getRejectionReason());
        e.setReviewedBy(r.getReviewedBy());
        e.setReviewedAt(r.getReviewedAt());
        e.setCreatedAt(r.getCreatedAt());
        e.setUpdatedAt(r.getUpdatedAt());
        return e;
    }

    public static ClientIntakeRequest toDomain(ClientIntakeRequestEntity e) {
        return new ClientIntakeRequest(
                e.getId(), e.getTenantId(), e.getAgencyId(), e.getBranchId(),
                e.getReferenceCode(), Source.valueOf(e.getSource()), Status.valueOf(e.getStatus()),
                e.getSenderName(), e.getSenderPhone(),
                e.getRecipientName(), e.getRecipientPhone(),
                e.getPickupAddress(), e.getDeliveryAddress(),
                e.getWeightKg(), e.getPackagesCount() != null ? e.getPackagesCount() : 1,
                DeliveryMode.valueOf(e.getDeliveryMode()), e.getTargetHubId(), e.getNotes(),
                e.getMissionId(), e.getTrackingCode(),
                e.getRejectionReason(), e.getReviewedBy(), e.getReviewedAt(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
