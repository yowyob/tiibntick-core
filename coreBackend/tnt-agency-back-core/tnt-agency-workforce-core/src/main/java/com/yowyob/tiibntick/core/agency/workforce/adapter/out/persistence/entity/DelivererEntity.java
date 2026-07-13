package com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table(schema = "agency_hr", name = "deliverers")
public class DelivererEntity {

    @Id @Column("id")              private UUID id;
    @Column("tenant_id")           private UUID tenantId;
    @Column("agency_id")           private UUID agencyId;
    @Column("branch_id")           private UUID branchId;
    @Column("actor_id")            private UUID actorId;
    @Column("phone")               private String phone;
    @Column("status")              private String status;
    @Column("joined_at")           private Instant joinedAt;
    @Column("suspended_at")        private Instant suspendedAt;
    @Column("created_at")          private Instant createdAt;
    @Column("updated_at")          private Instant updatedAt;
    @Version @Column("version")    private Long version;
}
