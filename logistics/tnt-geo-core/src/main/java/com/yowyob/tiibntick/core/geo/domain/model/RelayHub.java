package com.yowyob.tiibntick.core.geo.domain.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.yowyob.tiibntick.core.geo.domain.exception.RelayHubStateException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A physical parcel relay hub, optionally managed by an agency branch.
 * RelayHubs are RELAY_HUB-typed road network nodes that can temporarily store parcels.
 *
 * <p>{@code branchId} is {@code null} for a freelance/independent hub (e.g. a GOFP relay
 * point, which by construction has no agency) and non-null for an agency-managed hub.
 *
 * <p>Two separate statuses (decision D-138): the operating {@link HubStatus} (open, full, closed) and the
 * administrator's {@link HubApprovalStatus} (PENDING, APPROVED, SUSPENDED, REJECTED, REVOKED), changed only by
 * {@link #approve()}, {@link #reject()}, {@link #suspend()}, {@link #reactivate()} and
 * {@link #revoke(String, UUID)} along the couriers' machine. Revocation (D-129, D-135) is definitive, only from
 * APPROVED or SUSPENDED, with the reason, the date and the administrator. Only an APPROVED hub takes a new parcel
 * (D-140); parcels already held can always leave ({@link #removeParcel()}). Their return at the end of the storage
 * period is not wired here (events lot L2: overdue entries of the inventory).
 *
 * Author: MANFOUO Braun
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class RelayHub {

    private final UUID id;
    private final UUID tenantId;
    private final UUID branchId;
    private final RoadNodeId nodeId;
    private int capacitySlots;
    private int currentOccupancy;
    private final String operatorActorId;
    private HubStatus status;
    private HubApprovalStatus approvalStatus;
    private final Instant createdAt;
    private Instant updatedAt;
    private String revocationReason;
    private Instant revokedAt;
    private UUID revokedByActorId;

    /** Longest revocation reason kept (characters). */
    public static final int MAX_REASON_LENGTH = 1000;

    private RelayHub(UUID id, UUID tenantId, UUID branchId, RoadNodeId nodeId,
                     int capacitySlots, int currentOccupancy, String operatorActorId,
                     HubStatus status, HubApprovalStatus approvalStatus, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        // branchId is nullable: null means a freelance/independent hub with no agency branch.
        this.branchId = branchId;
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        if (capacitySlots <= 0) throw new IllegalArgumentException("capacitySlots must be > 0");
        if (currentOccupancy < 0) throw new IllegalArgumentException("currentOccupancy must be >= 0");
        if (currentOccupancy > capacitySlots) {
            throw new IllegalArgumentException("currentOccupancy cannot exceed capacitySlots");
        }
        this.capacitySlots = capacitySlots;
        this.currentOccupancy = currentOccupancy;
        this.operatorActorId = operatorActorId;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.approvalStatus = Objects.requireNonNull(approvalStatus, "approvalStatus must not be null");
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    /**
     * A new hub: open ({@link HubStatus#ACTIVE}) but waiting for an administrator's approval
     * ({@link HubApprovalStatus#PENDING}, D-138), so it is neither proposed nor takes parcels until approved.
     *
     * @param branchId agency branch owning this hub, or {@code null} for a
     *                 freelance/independent hub with no agency.
     */
    public static RelayHub create(UUID tenantId, UUID branchId, RoadNodeId nodeId,
                                  int capacitySlots, String operatorActorId) {
        Instant now = Instant.now();
        return new RelayHub(UUID.randomUUID(), tenantId, branchId, nodeId, capacitySlots, 0,
                operatorActorId, HubStatus.ACTIVE, HubApprovalStatus.PENDING, now, now);
    }

    /** Rehydrates an approved hub (the state of every hub that existed before D-138). */
    public static RelayHub rehydrate(UUID id, UUID tenantId, UUID branchId, RoadNodeId nodeId,
                                     int capacitySlots, int currentOccupancy, String operatorActorId,
                                     HubStatus status, Instant createdAt, Instant updatedAt) {
        return new RelayHub(id, tenantId, branchId, nodeId, capacitySlots, currentOccupancy,
                operatorActorId, status, HubApprovalStatus.APPROVED, createdAt, updatedAt);
    }

    /** Rehydrates a hub with its approval and revocation (reason, date, administrator: null unless revoked). */
    public static RelayHub rehydrate(UUID id, UUID tenantId, UUID branchId, RoadNodeId nodeId,
                                     int capacitySlots, int currentOccupancy, String operatorActorId,
                                     HubStatus status, HubApprovalStatus approvalStatus,
                                     Instant createdAt, Instant updatedAt,
                                     String revocationReason, Instant revokedAt, UUID revokedByActorId) {
        RelayHub hub = new RelayHub(id, tenantId, branchId, nodeId, capacitySlots, currentOccupancy,
                operatorActorId, status, approvalStatus, createdAt, updatedAt);
        hub.revocationReason = revocationReason;
        hub.revokedAt = revokedAt;
        hub.revokedByActorId = revokedByActorId;
        return hub;
    }

    /** PENDING → APPROVED (a suspended hub is reactivated, {@link #reactivate()}). */
    public void approve() {
        if (approvalStatus != HubApprovalStatus.PENDING) {
            throw refused(HubApprovalStatus.APPROVED);
        }
        transitionApproval(HubApprovalStatus.APPROVED);
    }

    /** PENDING → REJECTED (final). */
    public void reject() {
        transitionApproval(HubApprovalStatus.REJECTED);
    }

    /** APPROVED → SUSPENDED (D-139): no longer proposed, no new deposit (D-140). */
    public void suspend() {
        transitionApproval(HubApprovalStatus.SUSPENDED);
    }

    /** SUSPENDED → APPROVED (D-139). */
    public void reactivate() {
        if (approvalStatus != HubApprovalStatus.SUSPENDED) {
            throw refused(HubApprovalStatus.APPROVED);
        }
        transitionApproval(HubApprovalStatus.APPROVED);
    }

    /**
     * Revokes the hub for good (administrator action, D-129), only from APPROVED or SUSPENDED (D-135).
     *
     * @param reason    mandatory, not blank, at most {@link #MAX_REASON_LENGTH} characters
     * @param revokedBy the administrator's actor id, mandatory
     * @throws IllegalArgumentException when the reason or the administrator is missing
     * @throws RelayHubStateException   when the approval status does not allow it (already revoked included)
     */
    public void revoke(String reason, UUID revokedBy) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a revocation reason is mandatory");
        }
        String trimmed = reason.strip();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("the revocation reason exceeds " + MAX_REASON_LENGTH + " characters");
        }
        Objects.requireNonNull(revokedBy, "revokedBy must not be null");
        transitionApproval(HubApprovalStatus.REVOKED);
        this.revocationReason = trimmed;
        this.revokedAt = this.updatedAt;
        this.revokedByActorId = revokedBy;
    }

    private void transitionApproval(HubApprovalStatus target) {
        if (!approvalStatus.canTransitionTo(target)) {
            throw refused(target);
        }
        this.approvalStatus = target;
        this.updatedAt = Instant.now();
    }

    private RelayHubStateException refused(HubApprovalStatus target) {
        return new RelayHubStateException("Hub " + id + " is " + approvalStatus + ": " + target + " refused");
    }

    private void requireNotRevoked(String operation) {
        if (approvalStatus == HubApprovalStatus.REVOKED) {
            throw new RelayHubStateException("Hub " + id + " is revoked: " + operation + " refused");
        }
    }

    /** A new parcel: only in an APPROVED hub (D-140) with a free slot. */
    public void addParcel() {
        if (!approvalStatus.acceptsDeposits()) {
            throw new RelayHubStateException("Hub " + id + " is " + approvalStatus + ": no new deposit (D-140)");
        }
        if (currentOccupancy >= capacitySlots) {
            throw new IllegalStateException("Hub " + id + " is at full capacity");
        }
        this.currentOccupancy++;
        if (this.currentOccupancy == this.capacitySlots) {
            this.status = HubStatus.FULL;
        }
        this.updatedAt = Instant.now();
    }

    public void removeParcel() {
        if (currentOccupancy <= 0) {
            throw new IllegalStateException("Hub " + id + " is already empty");
        }
        this.currentOccupancy--;
        // parcels leave whatever the approval (D-140: picked up until the end of their storage period)
        if (this.status == HubStatus.FULL && this.currentOccupancy < this.capacitySlots) {
            this.status = HubStatus.ACTIVE;
        }
        this.updatedAt = Instant.now();
    }

    /**
     * Decision D-185 (2026-11-28): the capacity follows the number of parcels the relay operator declares he can hold
     * at once. Never below the parcels present ({@link IllegalStateException}); the FULL/ACTIVE state follows, a
     * closed or revoked hub stays closed.
     */
    public void resize(int slots) {
        if (slots <= 0) {
            throw new IllegalArgumentException("capacitySlots must be > 0");
        }
        if (slots < currentOccupancy) {
            throw new IllegalStateException("Hub " + id + " holds " + currentOccupancy
                    + " parcels: its capacity cannot go below that");
        }
        this.capacitySlots = slots;
        if (status == HubStatus.ACTIVE || status == HubStatus.FULL) {
            this.status = (currentOccupancy == capacitySlots) ? HubStatus.FULL : HubStatus.ACTIVE;
        }
        this.updatedAt = Instant.now();
    }

    public void updateOccupancy(int occupancy) {
        if (occupancy < 0 || occupancy > capacitySlots) {
            throw new IllegalArgumentException("occupancy " + occupancy + " is out of range [0, " + capacitySlots + "]");
        }
        this.currentOccupancy = occupancy;
        // the count of parcels never reopens a closed hub (a revoked one, closed by gdd-l4-421, included)
        if (status == HubStatus.ACTIVE || status == HubStatus.FULL) {
            this.status = (occupancy == capacitySlots) ? HubStatus.FULL : HubStatus.ACTIVE;
        }
        this.updatedAt = Instant.now();
    }

    public void close()          { requireNotRevoked("closing"); this.status = HubStatus.TEMPORARILY_CLOSED; this.updatedAt = Instant.now(); }
    public void reopen()         { requireNotRevoked("reopening"); this.status = HubStatus.ACTIVE; this.updatedAt = Instant.now(); }
    public void permanentlyClose(){ requireNotRevoked("closing"); this.status = HubStatus.PERMANENTLY_CLOSED; this.updatedAt = Instant.now(); }

    /** Proposed as a destination: approved (D-140) and open with room. */
    public boolean isAvailable() { return approvalStatus.acceptsDeposits() && status == HubStatus.ACTIVE; }
    /** D-140: takes new deposits (approved). */
    public boolean acceptsDeposits() { return approvalStatus.acceptsDeposits(); }
    public HubApprovalStatus approvalStatus() { return approvalStatus; }
    public int availableSlots()  { return capacitySlots - currentOccupancy; }
    public double occupancyRate(){ return (double) currentOccupancy / capacitySlots; }

    public UUID id()                  { return id; }
    public UUID tenantId()            { return tenantId; }
    public UUID branchId()            { return branchId; }
    public RoadNodeId nodeId()        { return nodeId; }
    public int capacitySlots()        { return capacitySlots; }
    public int currentOccupancy()     { return currentOccupancy; }
    public String operatorActorId()   { return operatorActorId; }
    public HubStatus status()         { return status; }
    public Instant createdAt()        { return createdAt; }
    public Instant updatedAt()        { return updatedAt; }
    public String revocationReason()  { return revocationReason; }
    public Instant revokedAt()        { return revokedAt; }
    public UUID revokedByActorId()    { return revokedByActorId; }
    public boolean isRevoked()        { return approvalStatus == HubApprovalStatus.REVOKED; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RelayHub that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "RelayHub{id=" + id + ", node=" + nodeId + ", occupancy="
                + currentOccupancy + "/" + capacitySlots + ", status=" + status + "}";
    }
}
