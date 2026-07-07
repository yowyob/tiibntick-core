package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("tnt_dispute_escalations")
public class DisputeEscalationEntity {

    @Id
    @Column("id")
    private String id;

    @Column("dispute_id")
    private String disputeId;

    @Column("tenant_id")
    private String tenantId;

    @Column("escalated_at")
    private LocalDateTime escalatedAt;

    @Column("escalated_by")
    private String escalatedBy;

    @Column("reason")
    private String reason;

    @Column("from_status")
    private String fromStatus;

    @Column("to_status")
    private String toStatus;

    @Column("assigned_to")
    private String assignedTo;

    public DisputeEscalationEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDisputeId() { return disputeId; }
    public void setDisputeId(String disputeId) { this.disputeId = disputeId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public LocalDateTime getEscalatedAt() { return escalatedAt; }
    public void setEscalatedAt(LocalDateTime escalatedAt) { this.escalatedAt = escalatedAt; }
    public String getEscalatedBy() { return escalatedBy; }
    public void setEscalatedBy(String escalatedBy) { this.escalatedBy = escalatedBy; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
}
