package com.yowyob.tiibntick.core.agency.workforce.domain;

import com.yowyob.tiibntick.core.agency.workforce.domain.vo.DelivererStatus;

import java.time.Instant;
import java.util.UUID;

/** Ported from tnt-agency {@code Deliverer}. */
public class Deliverer {

    private final UUID id;
    private final UUID tenantId;
    private final UUID agencyId;
    private UUID branchId;
    private final UUID actorId;
    private String phone;
    private DelivererStatus status;
    private final Instant joinedAt;
    private Instant suspendedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Deliverer(UUID id, UUID tenantId, UUID agencyId, UUID branchId, UUID actorId, String phone,
                     DelivererStatus status, Instant joinedAt, Instant suspendedAt,
                     Instant createdAt, Instant updatedAt, long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.agencyId = agencyId;
        this.branchId = branchId;
        this.actorId = actorId;
        this.phone = phone;
        this.status = status;
        this.joinedAt = joinedAt;
        this.suspendedAt = suspendedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static Deliverer register(UUID id, UUID tenantId, UUID agencyId, UUID actorId, String phone, Instant now) {
        return new Deliverer(id, tenantId, agencyId, null, actorId, phone,
                DelivererStatus.AVAILABLE, now, null, now, now, 0L);
    }

    public void attachToBranch(UUID branchId, Instant now) {
        this.branchId = branchId;
        this.updatedAt = now;
    }

    public void suspend(Instant now) {
        if (status != DelivererStatus.AVAILABLE) {
            throw new IllegalStateException("Only an AVAILABLE deliverer can be suspended");
        }
        this.status = DelivererStatus.SUSPENDED;
        this.suspendedAt = now;
        this.updatedAt = now;
    }

    public void reactivate(Instant now) {
        this.status = DelivererStatus.AVAILABLE;
        this.suspendedAt = null;
        this.updatedAt = now;
    }

    public void setAvailability(DelivererStatus target, Instant now) {
        if (target != DelivererStatus.AVAILABLE && target != DelivererStatus.OFFLINE) {
            throw new IllegalArgumentException("Only AVAILABLE and OFFLINE are allowed");
        }
        if (status == DelivererStatus.SUSPENDED || status == DelivererStatus.INACTIVE) {
            throw new IllegalStateException("Deliverer is not operational");
        }
        if (status == DelivererStatus.ON_MISSION && target == DelivererStatus.OFFLINE) {
            throw new IllegalStateException("Cannot go offline during a mission");
        }
        this.status = target;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getBranchId() { return branchId; }
    public UUID getActorId() { return actorId; }
    public String getPhone() { return phone; }
    public DelivererStatus getStatus() { return status; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getSuspendedAt() { return suspendedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
