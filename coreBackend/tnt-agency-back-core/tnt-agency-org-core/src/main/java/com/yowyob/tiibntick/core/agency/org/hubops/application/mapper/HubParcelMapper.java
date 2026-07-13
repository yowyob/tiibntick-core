package com.yowyob.tiibntick.core.agency.org.hubops.application.mapper;

import com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence.entity.HubParcelRecordEntity;
import com.yowyob.tiibntick.core.agency.org.hubops.domain.HubParcelRecord;
import com.yowyob.tiibntick.core.agency.org.hubops.domain.vo.ParcelStatus;

public final class HubParcelMapper {

    private HubParcelMapper() {}

    public static HubParcelRecord toDomain(HubParcelRecordEntity e) {
        return new HubParcelRecord(
                e.getId(), e.getTenantId(), e.getHubId(), e.getPackageId(), e.getMissionId(),
                e.getTrackingCode(), e.getDepositedAt(), e.getWithdrawalDeadline(),
                ParcelStatus.valueOf(e.getStatus()),
                Boolean.TRUE.equals(e.getIdentityVerified()),
                e.getWithdrawnBy(), e.getCoreHubPackageEntryId(),
                e.getCreatedAt(), e.getUpdatedAt(),
                e.getVersion() != null ? e.getVersion() : 0L);
    }

    public static HubParcelRecordEntity toEntity(HubParcelRecord r) {
        HubParcelRecordEntity e = new HubParcelRecordEntity();
        e.setId(r.getId());
        e.setTenantId(r.getTenantId());
        e.setHubId(r.getHubId());
        e.setPackageId(r.getPackageId());
        e.setMissionId(r.getMissionId());
        e.setTrackingCode(r.getTrackingCode());
        e.setDepositedAt(r.getDepositedAt());
        e.setWithdrawalDeadline(r.getWithdrawalDeadline());
        e.setStatus(r.getStatus().name());
        e.setIdentityVerified(r.isIdentityVerified());
        e.setWithdrawnBy(r.getWithdrawnBy());
        e.setCoreHubPackageEntryId(r.getCoreHubPackageEntryId());
        e.setCreatedAt(r.getCreatedAt());
        e.setUpdatedAt(r.getUpdatedAt());
        e.setVersion(r.getVersion());
        return e;
    }
}
