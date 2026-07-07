package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * R2DBC persistence entity for the {@code tnt_disputes} table.
 * Mapped to/from the {@link com.yowyob.tiibntick.core.dispute.domain.model.Dispute} aggregate
 * by {@code DisputePersistenceMapper}.
 *
 * <p>Scalar child collections (evidences, events, comments, escalations) are stored
 * in separate tables and loaded via separate repository queries.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_disputes")
public class DisputeEntity {

    @Id
    @Column("id")
    private String id;

    @Column("tenant_id")
    private String tenantId;

    @Column("reference")
    private String reference;

    @Column("cause")
    private String cause;

    @Column("category")
    private String category;

    @Column("priority")
    private String priority;

    @Column("status")
    private String status;

    @Column("claimant_id")
    private String claimantId;

    @Column("claimant_type")
    private String claimantType;

    @Column("respondent_id")
    private String respondentId;

    @Column("respondent_type")
    private String respondentType;

    @Column("mission_id")
    private String missionId;

    @Column("package_id")
    private String packageId;

    @Column("tracking_code")
    private String trackingCode;

    @Column("description")
    private String description;

    @Column("filed_at")
    private LocalDateTime filedAt;

    @Column("deadline")
    private LocalDateTime deadline;

    @Column("assigned_mediator_id")
    private String assignedMediatorId;

    // Resolution (flattened)
    @Column("resolution_type")
    private String resolutionType;

    @Column("resolution_compensation_required")
    private Boolean resolutionCompensationRequired;

    @Column("resolution_mediator_id")
    private String resolutionMediatorId;

    @Column("resolution_summary")
    private String resolutionSummary;

    @Column("resolution_occurred_at")
    private LocalDateTime resolutionOccurredAt;

    // Compensation (flattened)
    @Column("compensation_amount")
    private BigDecimal compensationAmount;

    @Column("compensation_currency")
    private String compensationCurrency;

    @Column("compensation_method")
    private String compensationMethod;

    @Column("compensation_beneficiary_id")
    private String compensationBeneficiaryId;

    @Column("compensation_payment_reference")
    private String compensationPaymentReference;

    @Column("compensation_approved_at")
    private LocalDateTime compensationApprovedAt;

    @Column("compensation_paid_at")
    private LocalDateTime compensationPaidAt;

    // SLA Policy (flattened)
    @Column("sla_response_hours")
    private Integer slaResponseHours;

    @Column("sla_investigation_days")
    private Integer slaInvestigationDays;

    @Column("sla_resolution_days")
    private Integer slaResolutionDays;

    @Column("sla_escalation_days")
    private Integer slaEscalationDays;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Version
    // : FreelancerOrg respondent context
    @Column("respondent_org_id")
    private String respondentOrgId;
    @Column("implied_sub_deliverer_id")
    private String impliedSubDelivererId;
    @Column("sub_deliverer_involved")
    private Boolean subDelivererInvolved;

    @Column("version")
    private Integer version;

    public DisputeEntity() {}

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getCause() { return cause; }
    public void setCause(String cause) { this.cause = cause; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getClaimantId() { return claimantId; }
    public void setClaimantId(String claimantId) { this.claimantId = claimantId; }
    public String getClaimantType() { return claimantType; }
    public void setClaimantType(String claimantType) { this.claimantType = claimantType; }
    public String getRespondentId() { return respondentId; }
    public void setRespondentId(String respondentId) { this.respondentId = respondentId; }
    public String getRespondentType() { return respondentType; }
    public void setRespondentType(String respondentType) { this.respondentType = respondentType; }
    public String getMissionId() { return missionId; }
    public void setMissionId(String missionId) { this.missionId = missionId; }
    public String getPackageId() { return packageId; }
    public void setPackageId(String packageId) { this.packageId = packageId; }
    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getFiledAt() { return filedAt; }
    public void setFiledAt(LocalDateTime filedAt) { this.filedAt = filedAt; }
    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
    public String getAssignedMediatorId() { return assignedMediatorId; }
    public void setAssignedMediatorId(String assignedMediatorId) { this.assignedMediatorId = assignedMediatorId; }
    public String getResolutionType() { return resolutionType; }
    public void setResolutionType(String resolutionType) { this.resolutionType = resolutionType; }
    public Boolean getResolutionCompensationRequired() { return resolutionCompensationRequired; }
    public void setResolutionCompensationRequired(Boolean v) { this.resolutionCompensationRequired = v; }
    public String getResolutionMediatorId() { return resolutionMediatorId; }
    public void setResolutionMediatorId(String v) { this.resolutionMediatorId = v; }
    public String getResolutionSummary() { return resolutionSummary; }
    public void setResolutionSummary(String v) { this.resolutionSummary = v; }
    public LocalDateTime getResolutionOccurredAt() { return resolutionOccurredAt; }
    public void setResolutionOccurredAt(LocalDateTime v) { this.resolutionOccurredAt = v; }
    public BigDecimal getCompensationAmount() { return compensationAmount; }
    public void setCompensationAmount(BigDecimal v) { this.compensationAmount = v; }
    public String getCompensationCurrency() { return compensationCurrency; }
    public void setCompensationCurrency(String v) { this.compensationCurrency = v; }
    public String getCompensationMethod() { return compensationMethod; }
    public void setCompensationMethod(String v) { this.compensationMethod = v; }
    public String getCompensationBeneficiaryId() { return compensationBeneficiaryId; }
    public void setCompensationBeneficiaryId(String v) { this.compensationBeneficiaryId = v; }
    public String getCompensationPaymentReference() { return compensationPaymentReference; }
    public void setCompensationPaymentReference(String v) { this.compensationPaymentReference = v; }
    public LocalDateTime getCompensationApprovedAt() { return compensationApprovedAt; }
    public void setCompensationApprovedAt(LocalDateTime v) { this.compensationApprovedAt = v; }
    public LocalDateTime getCompensationPaidAt() { return compensationPaidAt; }
    public void setCompensationPaidAt(LocalDateTime v) { this.compensationPaidAt = v; }
    public Integer getSlaResponseHours() { return slaResponseHours; }
    public void setSlaResponseHours(Integer v) { this.slaResponseHours = v; }
    public Integer getSlaInvestigationDays() { return slaInvestigationDays; }
    public void setSlaInvestigationDays(Integer v) { this.slaInvestigationDays = v; }
    public Integer getSlaResolutionDays() { return slaResolutionDays; }
    public void setSlaResolutionDays(Integer v) { this.slaResolutionDays = v; }
    public Integer getSlaEscalationDays() { return slaEscalationDays; }
    public void setSlaEscalationDays(Integer v) { this.slaEscalationDays = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    //  getters/setters
    public String getRespondentOrgId() { return respondentOrgId; }
    public void setRespondentOrgId(String v) { this.respondentOrgId = v; }
    public String getImpliedSubDelivererId() { return impliedSubDelivererId; }
    public void setImpliedSubDelivererId(String v) { this.impliedSubDelivererId = v; }
    public Boolean getSubDelivererInvolved() { return subDelivererInvolved; }
    public void setSubDelivererInvolved(Boolean v) { this.subDelivererInvolved = v; }
}
