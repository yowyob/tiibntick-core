package com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence.entity;

import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table(schema = "agency_org", name = "hub_handoff_requests")
public class HubHandoffRequestEntity implements Persistable<UUID>, TntPersistableEntity {

    @Transient private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }

    @Id @Column("id") private UUID id;
    @Column("tenant_id") private UUID tenantId;
    @Column("agency_id") private UUID agencyId;
    @Column("hub_id") private UUID hubId;
    @Column("handoff_type") private String handoffType;
    @Column("status") private String status;
    @Column("mission_id") private UUID missionId;
    @Column("package_id") private UUID packageId;
    @Column("tracking_code") private String trackingCode;
    @Column("requester_actor_id") private UUID requesterActorId;
    @Column("requester_role") private String requesterRole;
    @Column("requester_label") private String requesterLabel;
    @Column("withdraw_party") private String withdrawParty;
    @Column("validated_by_actor_id") private UUID validatedByActorId;
    @Column("validated_by_label") private String validatedByLabel;
    @Column("notes") private String notes;
    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
    @Column("validated_at") private Instant validatedAt;
    @Column("completed_at") private Instant completedAt;
    @Version @Column("version") private Long version;
}
