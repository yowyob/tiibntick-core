package com.yowyob.tiibntick.core.tp.domain.model;

import com.yowyob.tiibntick.core.tp.domain.model.enums.DocumentType;
import com.yowyob.tiibntick.core.tp.domain.model.enums.KycStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity: KycRecord.
 *
 * <p>Represents the KYC (Know Your Customer) verification record for a TiiBnTick
 * third party. Supports simplified KYC adapted to the informal Cameroonian context —
 * a single primary document + selfie is sufficient for basic verification.
 *
 * <p>The entity is immutable. Business state transitions (approve, reject, expire)
 * return new instances.
 *
 * <p>The {@code thirdPartyId} field is the Kernel integration key referencing the
 * ThirdParty entity in RT-comops-tp-core.
 *
 * @author MANFOUO Braun
 */
public final class KycRecord {

    private final UUID id;
    private final UUID tenantId;

    /**
     * Kernel integration key — references the ThirdParty in RT-comops-tp-core.
     * Must not be null.
     */
    private final UUID thirdPartyId;

    private final DocumentType documentType;

    /** Storage key for the document file (MinIO path via tnt-media-core). */
    private final String documentStorageKey;

    /** Storage key for the selfie photo. Nullable. */
    private final String selfieStorageKey;

    /** Document number (ID card number, passport number, etc.). Nullable. */
    private final String documentNumber;

    /** Document expiry date. Nullable (some document types have none). */
    private final LocalDate documentExpiryDate;

    private final KycStatus status;

    /** Rejection reason — set only when {@code status = REJECTED}. */
    private final String rejectionReason;

    /** Admin or automated reviewer ID. Nullable until reviewed. */
    private final UUID reviewedBy;

    private final Instant submittedAt;
    private final Instant reviewedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private KycRecord(
            UUID id, UUID tenantId, UUID thirdPartyId,
            DocumentType documentType, String documentStorageKey,
            String selfieStorageKey, String documentNumber,
            LocalDate documentExpiryDate, KycStatus status,
            String rejectionReason, UUID reviewedBy,
            Instant submittedAt, Instant reviewedAt,
            Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.thirdPartyId = Objects.requireNonNull(thirdPartyId,
                "thirdPartyId (Kernel integration key) is required");
        this.documentType = Objects.requireNonNull(documentType, "documentType is required");
        this.documentStorageKey = Objects.requireNonNull(documentStorageKey, "documentStorageKey is required");
        this.selfieStorageKey = selfieStorageKey;
        this.documentNumber = documentNumber;
        this.documentExpiryDate = documentExpiryDate;
        this.status = status != null ? status : KycStatus.PENDING_REVIEW;
        this.rejectionReason = rejectionReason;
        this.reviewedBy = reviewedBy;
        this.submittedAt = submittedAt;
        this.reviewedAt = reviewedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    // ─── Factory methods ─────────────────────────────────────────────────────

    /**
     * Creates a new KYC record upon document submission.
     * Status is set to {@link KycStatus#PENDING_REVIEW}.
     *
     * @param tenantId            the owning tenant
     * @param thirdPartyId        the Kernel ThirdParty reference UUID
     * @param documentType        the type of identity document
     * @param documentStorageKey  MinIO path for the uploaded document
     * @param selfieStorageKey    MinIO path for the selfie (nullable)
     * @param documentNumber      document identifier (nullable)
     * @param documentExpiryDate  expiry date (nullable)
     * @return new {@link KycRecord} in PENDING_REVIEW state
     */
    public static KycRecord submit(
            UUID tenantId, UUID thirdPartyId,
            DocumentType documentType, String documentStorageKey,
            String selfieStorageKey, String documentNumber,
            LocalDate documentExpiryDate) {
        Instant now = Instant.now();
        return new KycRecord(
                UUID.randomUUID(), tenantId, thirdPartyId,
                documentType, documentStorageKey, selfieStorageKey,
                documentNumber, documentExpiryDate,
                KycStatus.PENDING_REVIEW, null, null,
                now, null, now, now);
    }

    /**
     * Reconstitutes a {@link KycRecord} from persistence data.
     *
     * <p>Does not generate a new UUID or change any state. Used exclusively by
     * repository adapters to restore the entity from the database.
     *
     * @param id                  the persisted record UUID
     * @param tenantId            the tenant UUID
     * @param thirdPartyId        the Kernel ThirdParty reference UUID
     * @param documentType        the persisted document type
     * @param documentStorageKey  the persisted storage key
     * @param selfieStorageKey    the persisted selfie key (nullable)
     * @param documentNumber      the persisted document number (nullable)
     * @param documentExpiryDate  the persisted expiry date (nullable)
     * @param status              the persisted KYC status
     * @param rejectionReason     the persisted rejection reason (nullable)
     * @param reviewedBy          the persisted reviewer UUID (nullable)
     * @param submittedAt         the persisted submission timestamp
     * @param reviewedAt          the persisted review timestamp (nullable)
     * @param createdAt           the persisted creation timestamp
     * @param updatedAt           the persisted update timestamp
     * @return the reconstituted {@link KycRecord}
     */
    public static KycRecord reconstitute(
            UUID id, UUID tenantId, UUID thirdPartyId,
            DocumentType documentType, String documentStorageKey,
            String selfieStorageKey, String documentNumber,
            LocalDate documentExpiryDate, KycStatus status,
            String rejectionReason, UUID reviewedBy,
            Instant submittedAt, Instant reviewedAt,
            Instant createdAt, Instant updatedAt) {
        return new KycRecord(
                id, tenantId, thirdPartyId,
                documentType, documentStorageKey, selfieStorageKey,
                documentNumber, documentExpiryDate, status,
                rejectionReason, reviewedBy,
                submittedAt, reviewedAt, createdAt, updatedAt);
    }

    // ─── Business methods ─────────────────────────────────────────────────────

    /**
     * Approves the KYC record.
     *
     * @param reviewerId the ID of the reviewing administrator (must not be null)
     * @return approved {@link KycRecord} copy
     */
    public KycRecord approve(UUID reviewerId) {
        Objects.requireNonNull(reviewerId, "reviewerId is required");
        Instant now = Instant.now();
        return new KycRecord(
                id, tenantId, thirdPartyId,
                documentType, documentStorageKey, selfieStorageKey,
                documentNumber, documentExpiryDate,
                KycStatus.APPROVED, null, reviewerId,
                submittedAt, now, createdAt, now);
    }

    /**
     * Rejects the KYC record with an explanatory reason.
     *
     * @param reviewerId the reviewing administrator (must not be null)
     * @param reason     the rejection reason (must not be null)
     * @return rejected {@link KycRecord} copy
     */
    public KycRecord reject(UUID reviewerId, String reason) {
        Objects.requireNonNull(reviewerId, "reviewerId is required");
        Objects.requireNonNull(reason, "rejection reason is required");
        Instant now = Instant.now();
        return new KycRecord(
                id, tenantId, thirdPartyId,
                documentType, documentStorageKey, selfieStorageKey,
                documentNumber, documentExpiryDate,
                KycStatus.REJECTED, reason, reviewerId,
                submittedAt, now, createdAt, now);
    }

    /**
     * Marks the record as expired (document validity lapsed).
     *
     * @return expired {@link KycRecord} copy
     */
    public KycRecord expire() {
        Instant now = Instant.now();
        return new KycRecord(
                id, tenantId, thirdPartyId,
                documentType, documentStorageKey, selfieStorageKey,
                documentNumber, documentExpiryDate,
                KycStatus.EXPIRED, rejectionReason, reviewedBy,
                submittedAt, reviewedAt, createdAt, now);
    }

    /**
     * Returns whether this record is approved.
     *
     * @return {@code true} if status is {@link KycStatus#APPROVED}
     */
    public boolean isApproved() {
        return KycStatus.APPROVED.equals(status);
    }

    /**
     * Returns whether this record is awaiting review.
     *
     * @return {@code true} if status is {@link KycStatus#PENDING_REVIEW}
     */
    public boolean isPendingReview() {
        return KycStatus.PENDING_REVIEW.equals(status);
    }

    // ─── Accessors ───────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getThirdPartyId() { return thirdPartyId; }
    public DocumentType getDocumentType() { return documentType; }
    public String getDocumentStorageKey() { return documentStorageKey; }
    public String getSelfieStorageKey() { return selfieStorageKey; }
    public String getDocumentNumber() { return documentNumber; }
    public LocalDate getDocumentExpiryDate() { return documentExpiryDate; }
    public KycStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KycRecord that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
