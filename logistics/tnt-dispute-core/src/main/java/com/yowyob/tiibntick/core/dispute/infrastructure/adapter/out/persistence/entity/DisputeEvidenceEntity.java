package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * R2DBC persistence entity for the {@code tnt_dispute_evidences} table.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_dispute_evidences")
public class DisputeEvidenceEntity {

    @Id
    @Column("id")
    private String id;

    @Column("dispute_id")
    private String disputeId;

    @Column("submitted_by")
    private String submittedBy;

    @Column("submitter_type")
    private String submitterType;

    @Column("evidence_type")
    private String evidenceType;

    @Column("file_key")
    private String fileKey;

    @Column("description")
    private String description;

    @Column("submitted_at")
    private LocalDateTime submittedAt;

    @Column("is_verified")
    private boolean verified;

    @Column("verified_at")
    private LocalDateTime verifiedAt;

    @Column("verified_by_mediator_id")
    private String verifiedByMediatorId;

    @Column("blockchain_ref")
    private String blockchainRef;

    @Column("tenant_id")
    private String tenantId;

    @Column("evidence_hash")
    private String evidenceHash;

    public DisputeEvidenceEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDisputeId() { return disputeId; }
    public void setDisputeId(String disputeId) { this.disputeId = disputeId; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public String getSubmitterType() { return submitterType; }
    public void setSubmitterType(String submitterType) { this.submitterType = submitterType; }
    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String evidenceType) { this.evidenceType = evidenceType; }
    public String getFileKey() { return fileKey; }
    public void setFileKey(String fileKey) { this.fileKey = fileKey; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getVerifiedByMediatorId() { return verifiedByMediatorId; }
    public void setVerifiedByMediatorId(String verifiedByMediatorId) { this.verifiedByMediatorId = verifiedByMediatorId; }
    public String getBlockchainRef() { return blockchainRef; }
    public void setBlockchainRef(String blockchainRef) { this.blockchainRef = blockchainRef; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getEvidenceHash() { return evidenceHash; }
    public void setEvidenceHash(String evidenceHash) { this.evidenceHash = evidenceHash; }
}
