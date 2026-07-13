package com.yowyob.tiibntick.core.agency.staff.application.mapper;

import com.yowyob.tiibntick.core.agency.staff.adapter.in.web.dto.StaffMemberResponse;
import com.yowyob.tiibntick.core.agency.staff.adapter.out.persistence.entity.StaffMemberEntity;
import com.yowyob.tiibntick.core.agency.staff.domain.AgencyStaffMember;
import com.yowyob.tiibntick.core.agency.staff.domain.vo.StaffRole;
import com.yowyob.tiibntick.core.agency.staff.domain.vo.StaffStatus;

public final class StaffMemberMapper {

    private StaffMemberMapper() {}

    public static StaffMemberResponse toResponse(AgencyStaffMember m) {
        return new StaffMemberResponse(
                m.getId(), m.getAgencyId(), m.getBranchId(),
                m.getFullName(), m.getPhone(), m.getEmail(),
                m.getRole().name(), m.getStatus().name(), m.getJoinedAt());
    }

    public static StaffMemberEntity toEntity(AgencyStaffMember m) {
        StaffMemberEntity e = new StaffMemberEntity();
        e.setId(m.getId());
        e.setTenantId(m.getTenantId());
        e.setAgencyId(m.getAgencyId());
        e.setBranchId(m.getBranchId());
        e.setFullName(m.getFullName());
        e.setPhone(m.getPhone());
        e.setEmail(m.getEmail());
        e.setRole(m.getRole().name());
        e.setStatus(m.getStatus().name());
        e.setJoinedAt(m.getJoinedAt());
        e.setSuspendedAt(m.getSuspendedAt());
        e.setCreatedAt(m.getCreatedAt());
        e.setUpdatedAt(m.getUpdatedAt());
        e.setVersion(m.getVersion());
        return e;
    }

    public static AgencyStaffMember toDomain(StaffMemberEntity e) {
        return new AgencyStaffMember(
                e.getId(), e.getTenantId(), e.getAgencyId(), e.getBranchId(),
                e.getFullName(), e.getPhone(), e.getEmail(),
                StaffRole.valueOf(e.getRole()), StaffStatus.valueOf(e.getStatus()),
                e.getJoinedAt(), e.getSuspendedAt(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getVersion() != null ? e.getVersion() : 0L
        );
    }
}
