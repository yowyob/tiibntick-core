package com.yowyob.tiibntick.core.agency.onboarding.domain;

import java.time.Instant;
import java.util.UUID;

/** Ported from tnt-agency {@code OnboardingApplication}. */
public class OnboardingApplication {

    public enum ApplicationStatus { SUBMITTED, APPROVED, REJECTED }

    private final UUID id;
    private final UUID tenantId;
    private final UUID agencyId;
    private final UUID applicantUserId;
    private String legalName;
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;
    private String ownerNationalId;
    private String ownerIdType;
    private String docCniKey;
    private String docRccmKey;
    private String docProofKey;
    private ApplicationStatus applicationStatus;
    private String rejectionReason;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private UUID kernelBusinessActorId;
    private Instant kernelIdentityCompletedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public OnboardingApplication(UUID id, UUID tenantId, UUID agencyId, UUID applicantUserId,
                                 String legalName, String ownerName, String ownerEmail,
                                 String ownerPhone, String ownerNationalId, String ownerIdType,
                                 String docCniKey, String docRccmKey, String docProofKey,
                                 ApplicationStatus applicationStatus, String rejectionReason,
                                 UUID reviewedBy, Instant reviewedAt,
                                 UUID kernelBusinessActorId, Instant kernelIdentityCompletedAt,
                                 Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.agencyId = agencyId;
        this.applicantUserId = applicantUserId;
        this.legalName = legalName;
        this.ownerName = ownerName;
        this.ownerEmail = ownerEmail;
        this.ownerPhone = ownerPhone;
        this.ownerNationalId = ownerNationalId;
        this.ownerIdType = ownerIdType;
        this.docCniKey = docCniKey;
        this.docRccmKey = docRccmKey;
        this.docProofKey = docProofKey;
        this.applicationStatus = applicationStatus;
        this.rejectionReason = rejectionReason;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.kernelBusinessActorId = kernelBusinessActorId;
        this.kernelIdentityCompletedAt = kernelIdentityCompletedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static OnboardingApplication submit(UUID id, UUID tenantId, UUID agencyId,
                                               UUID applicantUserId, String legalName,
                                               String ownerName, String ownerEmail,
                                               String ownerPhone, String ownerNationalId,
                                               String ownerIdType, String docCniKey,
                                               String docRccmKey, String docProofKey,
                                               Instant now) {
        return new OnboardingApplication(
                id, tenantId, agencyId, applicantUserId, legalName,
                ownerName, ownerEmail, ownerPhone, ownerNationalId, ownerIdType,
                docCniKey, docRccmKey, docProofKey,
                ApplicationStatus.SUBMITTED, null, null, null,
                null, null, now, now);
    }

    public void approve(UUID reviewerId, Instant now) {
        this.applicationStatus = ApplicationStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.updatedAt = now;
    }

    public void reject(String reason, UUID reviewerId, Instant now) {
        this.applicationStatus = ApplicationStatus.REJECTED;
        this.rejectionReason = reason;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.updatedAt = now;
    }

    public void linkKernelBusinessActor(UUID businessActorId, Instant now) {
        if (businessActorId == null) {
            throw new IllegalArgumentException("kernel business actor id is required");
        }
        this.kernelBusinessActorId = businessActorId;
        this.kernelIdentityCompletedAt = now;
        this.updatedAt = now;
    }

    public boolean isKernelIdentityReady() {
        return kernelBusinessActorId != null;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getApplicantUserId() { return applicantUserId; }
    public String getLegalName() { return legalName; }
    public String getOwnerName() { return ownerName; }
    public String getOwnerEmail() { return ownerEmail; }
    public String getOwnerPhone() { return ownerPhone; }
    public String getOwnerNationalId() { return ownerNationalId; }
    public String getOwnerIdType() { return ownerIdType; }
    public String getDocCniKey() { return docCniKey; }
    public String getDocRccmKey() { return docRccmKey; }
    public String getDocProofKey() { return docProofKey; }
    public ApplicationStatus getApplicationStatus() { return applicationStatus; }
    public String getRejectionReason() { return rejectionReason; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public UUID getKernelBusinessActorId() { return kernelBusinessActorId; }
    public Instant getKernelIdentityCompletedAt() { return kernelIdentityCompletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
