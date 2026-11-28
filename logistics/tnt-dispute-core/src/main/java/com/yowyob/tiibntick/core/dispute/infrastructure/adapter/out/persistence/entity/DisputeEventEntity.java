package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.entity;

import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("tnt_dispute_events")
public class DisputeEventEntity implements Persistable<String>, TntPersistableEntity {

    /** Lot L4: inserted when new (client-side id), updated once read back or found existing. */
    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }


    @Id
    @Column("id")
    private String id;

    @Column("dispute_id")
    private String disputeId;

    @Column("tenant_id")
    private String tenantId;

    @Column("type")
    private String type;

    @Column("description")
    private String description;

    @Column("performed_by")
    private String performedBy;

    @Column("performed_by_type")
    private String performedByType;

    @Column("occurred_at")
    private LocalDateTime occurredAt;

    @Column("metadata_json")
    private String metadataJson;

    public DisputeEventEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDisputeId() { return disputeId; }
    public void setDisputeId(String disputeId) { this.disputeId = disputeId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }
    public String getPerformedByType() { return performedByType; }
    public void setPerformedByType(String performedByType) { this.performedByType = performedByType; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
