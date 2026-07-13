package com.yowyob.tiibntick.core.agency.staff.domain;

import com.yowyob.tiibntick.core.agency.staff.domain.vo.StaffRole;
import com.yowyob.tiibntick.core.agency.staff.domain.vo.StaffStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Agency administrative staff — ported from {@code AgencyStaffMember} in tnt-agency.
 */
public class AgencyStaffMember {

    private final UUID id;
    private final UUID tenantId;
    private final UUID agencyId;
    private UUID branchId;
    private String fullName;
    private String phone;
    private String email;
    private StaffRole role;
    private StaffStatus status;
    private final Instant joinedAt;
    private Instant suspendedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public AgencyStaffMember(UUID id, UUID tenantId, UUID agencyId, UUID branchId,
                             String fullName, String phone, String email,
                             StaffRole role, StaffStatus status,
                             Instant joinedAt, Instant suspendedAt,
                             Instant createdAt, Instant updatedAt, long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.agencyId = agencyId;
        this.branchId = branchId;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.role = role;
        this.status = status;
        this.joinedAt = joinedAt;
        this.suspendedAt = suspendedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static AgencyStaffMember register(UUID id, UUID tenantId, UUID agencyId, UUID branchId,
                                             String fullName, String phone, String email,
                                             StaffRole role, Instant now) {
        return new AgencyStaffMember(
                id, tenantId, agencyId, branchId, fullName, phone, email != null ? email : "",
                role, StaffStatus.ACTIVE, now, null, now, now, 0L
        );
    }

    public void update(String fullName, String phone, String email, StaffRole role,
                       UUID branchId, Instant now) {
        if (fullName != null) this.fullName = fullName;
        if (phone != null) this.phone = phone;
        if (email != null) this.email = email;
        if (role != null) this.role = role;
        if (branchId != null) this.branchId = branchId;
        this.updatedAt = now;
    }

    public void suspend(Instant now) {
        if (status != StaffStatus.ACTIVE) {
            throw new IllegalStateException("Only an ACTIVE staff member can be suspended");
        }
        this.status = StaffStatus.SUSPENDED;
        this.suspendedAt = now;
        this.updatedAt = now;
    }

    public void reactivate(Instant now) {
        if (status != StaffStatus.SUSPENDED) {
            throw new IllegalStateException("Only a SUSPENDED staff member can be reactivated");
        }
        this.status = StaffStatus.ACTIVE;
        this.suspendedAt = null;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getBranchId() { return branchId; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public StaffRole getRole() { return role; }
    public StaffStatus getStatus() { return status; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getSuspendedAt() { return suspendedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
