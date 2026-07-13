package com.yowyob.tiibntick.core.agency.commission.application.mapper;

import com.yowyob.tiibntick.core.agency.commission.adapter.in.web.dto.CommissionResponse;
import com.yowyob.tiibntick.core.agency.commission.adapter.out.persistence.entity.CommissionRecordEntity;
import com.yowyob.tiibntick.core.agency.commission.domain.CommissionRecord;
import com.yowyob.tiibntick.core.agency.commission.domain.vo.CommissionStatus;

public final class CommissionMapper {

    private CommissionMapper() {}

    public static CommissionResponse toResponse(CommissionRecord r) {
        return new CommissionResponse(
                r.getId(), r.getTenantId(), r.getAgencyId(), r.getDelivererId(), r.getMissionId(),
                r.getAmount(), r.getCurrency(), r.getStatus().name(), r.getDisputeReason(),
                r.getPaidAt(), r.getCreatedAt());
    }

    public static CommissionRecordEntity toEntity(CommissionRecord r) {
        CommissionRecordEntity e = new CommissionRecordEntity();
        e.setId(r.getId());
        e.setTenantId(r.getTenantId());
        e.setAgencyId(r.getAgencyId());
        e.setDelivererId(r.getDelivererId());
        e.setMissionId(r.getMissionId());
        e.setAmount(r.getAmount());
        e.setCurrency(r.getCurrency());
        e.setStatus(r.getStatus().name());
        e.setDisputeReason(r.getDisputeReason());
        e.setPaidAt(r.getPaidAt());
        e.setCreatedAt(r.getCreatedAt());
        e.setUpdatedAt(r.getUpdatedAt());
        e.setVersion(r.getVersion());
        return e;
    }

    public static CommissionRecord toDomain(CommissionRecordEntity e) {
        return new CommissionRecord(
                e.getId(), e.getTenantId(), e.getAgencyId(), e.getDelivererId(), e.getMissionId(),
                e.getAmount(), e.getCurrency(),
                CommissionStatus.valueOf(e.getStatus()),
                e.getDisputeReason(), e.getPaidAt(),
                e.getCreatedAt(), e.getUpdatedAt(),
                e.getVersion() != null ? e.getVersion() : 0L);
    }
}
