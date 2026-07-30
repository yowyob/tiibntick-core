package com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.entity;

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
@Table(schema = "agency_hr", name = "deliverers")
public class DelivererEntity implements Persistable<UUID>, TntPersistableEntity {

    @Transient private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }

    @Id @Column("id")              private UUID id;
    @Column("tenant_id")           private UUID tenantId;
    @Column("agency_id")           private UUID agencyId;
    @Column("branch_id")           private UUID branchId;
    @Column("actor_id")            private UUID actorId;
    @Column("phone")               private String phone;
    @Column("status")              private String status;
    @Column("joined_at")           private Instant joinedAt;
    @Column("suspended_at")        private Instant suspendedAt;
    @Column("last_latitude")       private Double lastLatitude;
    @Column("last_longitude")      private Double lastLongitude;
    @Column("last_accuracy_meters") private Double lastAccuracyMeters;
    @Column("last_location_at")    private Instant lastLocationAt;
    @Column("last_mission_id")     private UUID lastMissionId;
    @Column("created_at")          private Instant createdAt;
    @Column("updated_at")          private Instant updatedAt;
    @Version @Column("version")    private Long version;
}
