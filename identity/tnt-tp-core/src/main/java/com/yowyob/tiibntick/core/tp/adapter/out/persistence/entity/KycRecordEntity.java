package com.yowyob.tiibntick.core.tp.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * R2DBC entity for KycRecord persistence.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_kyc_records")
public class KycRecordEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;
    @Column("tenant_id") private UUID tenantId;
    @Column("third_party_id") private UUID thirdPartyId;
    @Column("document_type") private String documentType;
    @Column("document_storage_key") private String documentStorageKey;
    @Column("selfie_storage_key") private String selfieStorageKey;
    @Column("document_number") private String documentNumber;
    @Column("document_expiry_date") private LocalDate documentExpiryDate;
    @Column("status") private String status;
    @Column("rejection_reason") private String rejectionReason;
    @Column("reviewed_by") private UUID reviewedBy;
    @Column("submitted_at") private Instant submittedAt;
    @Column("reviewed_at") private Instant reviewedAt;
    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;

    public KycRecordEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getThirdPartyId() { return thirdPartyId; }
    public void setThirdPartyId(UUID thirdPartyId) { this.thirdPartyId = thirdPartyId; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getDocumentStorageKey() { return documentStorageKey; }
    public void setDocumentStorageKey(String documentStorageKey) { this.documentStorageKey = documentStorageKey; }
    public String getSelfieStorageKey() { return selfieStorageKey; }
    public void setSelfieStorageKey(String selfieStorageKey) { this.selfieStorageKey = selfieStorageKey; }
    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public LocalDate getDocumentExpiryDate() { return documentExpiryDate; }
    public void setDocumentExpiryDate(LocalDate documentExpiryDate) { this.documentExpiryDate = documentExpiryDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
