package com.yowyob.tiibntick.core.agency.assignment.domain;

import com.yowyob.tiibntick.core.agency.assignment.domain.vo.MissionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Agency projection of a delivery mission — sync/offline MISSION aggregate. */
public class AgencyMission {

    private final UUID id;
    private final UUID tenantId;
    private final UUID agencyId;
    private final UUID coreMissionId;
    private UUID assignedDelivererId;
    private UUID assignedVehicleId;
    private MissionStatus status;
    private Instant scheduledAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private String cancellationReason;
    private BigDecimal quotedAmount;
    private String quotedCurrency;
    private UUID branchId;
    private String pickupAddress;
    private String deliveryAddress;
    private String senderName;
    private String recipientName;
    private String recipientPhone;
    private Double weightKg;
    private Double distanceKm;
    private Integer packagesCount;
    private String priority;
    private UUID targetHubId;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public AgencyMission(UUID id, UUID tenantId, UUID agencyId, UUID coreMissionId,
                         UUID assignedDelivererId, UUID assignedVehicleId,
                         MissionStatus status, Instant scheduledAt,
                         Instant startedAt, Instant completedAt,
                         Instant cancelledAt, String cancellationReason,
                         BigDecimal quotedAmount, String quotedCurrency,
                         UUID branchId, String pickupAddress, String deliveryAddress,
                         String senderName, String recipientName, String recipientPhone,
                         Double weightKg, Double distanceKm, Integer packagesCount,
                         String priority, UUID targetHubId,
                         Instant createdAt, Instant updatedAt, long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.agencyId = agencyId;
        this.coreMissionId = coreMissionId;
        this.assignedDelivererId = assignedDelivererId;
        this.assignedVehicleId = assignedVehicleId;
        this.status = status;
        this.scheduledAt = scheduledAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.cancelledAt = cancelledAt;
        this.cancellationReason = cancellationReason;
        this.quotedAmount = quotedAmount;
        this.quotedCurrency = quotedCurrency;
        this.branchId = branchId;
        this.pickupAddress = pickupAddress;
        this.deliveryAddress = deliveryAddress;
        this.senderName = senderName;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.weightKg = weightKg;
        this.distanceKm = distanceKm;
        this.packagesCount = packagesCount;
        this.priority = priority;
        this.targetHubId = targetHubId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static AgencyMission create(UUID id, UUID tenantId, UUID agencyId,
                                       UUID coreMissionId, Instant scheduledAt, Instant now) {
        return new AgencyMission(id, tenantId, agencyId, coreMissionId,
                null, null, MissionStatus.PENDING, scheduledAt,
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                now, now, 0L);
    }

    public void applyCreationSnapshot(UUID branchId, String pickupAddress, String deliveryAddress,
                                      String senderName, String recipientName, String recipientPhone,
                                      Double weightKg, Double distanceKm, Integer packagesCount,
                                      String priority, UUID targetHubId, Instant now) {
        this.branchId = branchId;
        this.pickupAddress = pickupAddress;
        this.deliveryAddress = deliveryAddress;
        this.senderName = senderName;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.weightKg = weightKg;
        this.distanceKm = distanceKm;
        this.packagesCount = packagesCount;
        this.priority = priority;
        this.targetHubId = targetHubId;
        markUpdated(now);
    }

    public void assign(UUID delivererId, UUID vehicleId, Instant now) {
        if (status != MissionStatus.PENDING) {
            throw new IllegalStateException("Mission cannot be assigned in its current state");
        }
        this.assignedDelivererId = delivererId;
        this.assignedVehicleId = vehicleId;
        this.status = MissionStatus.ASSIGNED;
        markUpdated(now);
    }

    public void reassign(UUID delivererId, UUID vehicleId, Instant now) {
        if (status != MissionStatus.ASSIGNED) {
            throw new IllegalStateException("Reassignment only applies to ASSIGNED missions");
        }
        this.assignedDelivererId = delivererId;
        this.assignedVehicleId = vehicleId;
        markUpdated(now);
    }

    public void cancel(String reason, Instant now) {
        if (status == MissionStatus.DELIVERED || status == MissionStatus.CANCELLED) {
            throw new IllegalStateException("Mission cannot be cancelled in its current state");
        }
        this.status = MissionStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledAt = now;
        markUpdated(now);
    }

    public void reschedule(Instant newScheduledAt, Instant now) {
        if (status == MissionStatus.DELIVERED || status == MissionStatus.CANCELLED) {
            throw new IllegalStateException("Mission cannot be rescheduled in its current state");
        }
        this.status = MissionStatus.PENDING;
        this.scheduledAt = newScheduledAt;
        markUpdated(now);
    }

    public void start(Instant now) {
        if (status != MissionStatus.ASSIGNED) {
            throw new IllegalStateException("Only an ASSIGNED mission can be started");
        }
        this.status = MissionStatus.IN_TRANSIT;
        this.startedAt = now;
        markUpdated(now);
    }

    public void confirmDelivery(Instant now) {
        if (status != MissionStatus.IN_TRANSIT) {
            throw new IllegalStateException("Only an IN_TRANSIT mission can be delivered");
        }
        this.status = MissionStatus.DELIVERED;
        this.completedAt = now;
        markUpdated(now);
    }

    public void syncDeliveredFromCore(Instant now) {
        if (status == MissionStatus.DELIVERED || status == MissionStatus.CANCELLED) {
            return;
        }
        this.status = MissionStatus.DELIVERED;
        this.completedAt = now;
        markUpdated(now);
    }

    public void depositAtHub(Instant now) {
        if (status != MissionStatus.IN_TRANSIT) {
            throw new IllegalStateException("Only an IN_TRANSIT mission can be deposited at a hub");
        }
        this.status = MissionStatus.AT_HUB;
        markUpdated(now);
    }

    public void fail(String reason, Instant now) {
        if (status == MissionStatus.DELIVERED || status == MissionStatus.CANCELLED || status == MissionStatus.FAILED) {
            throw new IllegalStateException("Mission is already in a terminal state");
        }
        this.status = MissionStatus.FAILED;
        this.cancellationReason = reason;
        this.cancelledAt = now;
        markUpdated(now);
    }

    public void applyCoreProjection(
            MissionStatus status,
            Instant pickupTime,
            Instant deliveryTime,
            UUID deliveryPersonId,
            Instant now) {
        this.status = status;
        if (pickupTime != null) {
            this.startedAt = pickupTime;
        } else if (status == MissionStatus.IN_TRANSIT && this.startedAt == null) {
            this.startedAt = now;
        }
        if (deliveryTime != null) {
            this.completedAt = deliveryTime;
        } else if (status == MissionStatus.DELIVERED && this.completedAt == null) {
            this.completedAt = now;
        }
        if (status == MissionStatus.CANCELLED || status == MissionStatus.FAILED) {
            this.cancelledAt = now;
        }
        if (deliveryPersonId != null) {
            this.assignedDelivererId = deliveryPersonId;
        }
        markUpdated(now);
    }

    private void markUpdated(Instant now) {
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getCoreMissionId() { return coreMissionId; }
    public UUID getAssignedDelivererId() { return assignedDelivererId; }
    public UUID getAssignedVehicleId() { return assignedVehicleId; }
    public MissionStatus getStatus() { return status; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
    public BigDecimal getQuotedAmount() { return quotedAmount; }
    public String getQuotedCurrency() { return quotedCurrency; }
    public UUID getBranchId() { return branchId; }
    public String getPickupAddress() { return pickupAddress; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public String getSenderName() { return senderName; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public Double getWeightKg() { return weightKg; }
    public Double getDistanceKm() { return distanceKm; }
    public Integer getPackagesCount() { return packagesCount; }
    public String getPriority() { return priority; }
    public UUID getTargetHubId() { return targetHubId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
