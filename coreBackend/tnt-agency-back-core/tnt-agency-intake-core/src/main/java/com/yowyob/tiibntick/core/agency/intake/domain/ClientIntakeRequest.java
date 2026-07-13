package com.yowyob.tiibntick.core.agency.intake.domain;

import java.time.Instant;
import java.util.UUID;

/** Ported from tnt-agency {@code ClientIntakeRequest}. */
public class ClientIntakeRequest {

    public enum Source { MOBILE, WALK_IN }

    public enum Status { SUBMITTED, APPROVED, REJECTED }

    public enum DeliveryMode { DIRECT, HUB }

    private final UUID id;
    private final UUID tenantId;
    private final UUID agencyId;
    private final UUID branchId;
    private final String referenceCode;
    private final Source source;
    private Status status;
    private String senderName;
    private String senderPhone;
    private String recipientName;
    private String recipientPhone;
    private String pickupAddress;
    private String deliveryAddress;
    private Double weightKg;
    private int packagesCount;
    private DeliveryMode deliveryMode;
    private UUID targetHubId;
    private String notes;
    private UUID missionId;
    private String trackingCode;
    private String rejectionReason;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public ClientIntakeRequest(UUID id, UUID tenantId, UUID agencyId, UUID branchId,
                               String referenceCode, Source source, Status status,
                               String senderName, String senderPhone,
                               String recipientName, String recipientPhone,
                               String pickupAddress, String deliveryAddress,
                               Double weightKg, int packagesCount,
                               DeliveryMode deliveryMode, UUID targetHubId, String notes,
                               UUID missionId, String trackingCode,
                               String rejectionReason, UUID reviewedBy, Instant reviewedAt,
                               Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.agencyId = agencyId;
        this.branchId = branchId;
        this.referenceCode = referenceCode;
        this.source = source;
        this.status = status;
        this.senderName = senderName;
        this.senderPhone = senderPhone;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.pickupAddress = pickupAddress;
        this.deliveryAddress = deliveryAddress;
        this.weightKg = weightKg;
        this.packagesCount = packagesCount;
        this.deliveryMode = deliveryMode;
        this.targetHubId = targetHubId;
        this.notes = notes;
        this.missionId = missionId;
        this.trackingCode = trackingCode;
        this.rejectionReason = rejectionReason;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ClientIntakeRequest submit(UUID id, UUID tenantId, UUID agencyId, UUID branchId,
                                             String referenceCode, Source source,
                                             String senderName, String senderPhone,
                                             String recipientName, String recipientPhone,
                                             String pickupAddress, String deliveryAddress,
                                             Double weightKg, int packagesCount,
                                             DeliveryMode deliveryMode, UUID targetHubId,
                                             String notes, Instant now) {
        return new ClientIntakeRequest(
                id, tenantId, agencyId, branchId, referenceCode, source, Status.SUBMITTED,
                senderName, senderPhone, recipientName, recipientPhone,
                pickupAddress, deliveryAddress, weightKg, packagesCount,
                deliveryMode, targetHubId, notes,
                null, null, null, null, null, now, now);
    }

    public void approve(UUID missionId, String trackingCode, UUID reviewerId, Instant now) {
        this.status = Status.APPROVED;
        this.missionId = missionId;
        this.trackingCode = trackingCode;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.updatedAt = now;
    }

    public void reject(String reason, UUID reviewerId, Instant now) {
        this.status = Status.REJECTED;
        this.rejectionReason = reason;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getBranchId() { return branchId; }
    public String getReferenceCode() { return referenceCode; }
    public Source getSource() { return source; }
    public Status getStatus() { return status; }
    public String getSenderName() { return senderName; }
    public String getSenderPhone() { return senderPhone; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getPickupAddress() { return pickupAddress; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public Double getWeightKg() { return weightKg; }
    public int getPackagesCount() { return packagesCount; }
    public DeliveryMode getDeliveryMode() { return deliveryMode; }
    public UUID getTargetHubId() { return targetHubId; }
    public String getNotes() { return notes; }
    public UUID getMissionId() { return missionId; }
    public String getTrackingCode() { return trackingCode; }
    public String getRejectionReason() { return rejectionReason; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
