package com.yowyob.tiibntick.core.sync.adapter.out.persistence.entity;

import com.yowyob.tiibntick.core.sync.domain.model.EntityVersionRecord;
import com.yowyob.tiibntick.core.sync.domain.model.enums.DeltaOperation;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("tnt_entity_version")
public class EntityVersionEntity {

    @Id
    @Column("id")
    public Long id;

    @Column("tenant_id")
    public String tenantId;

    @Column("aggregate_type")
    public String aggregateType;

    @Column("aggregate_id")
    public String aggregateId;

    @Column("version")
    public long version;

    @Column("operation")
    public String operation;

    @Column("payload_json")
    public String payloadJson;

    @Column("updated_at")
    public LocalDateTime updatedAt;

    @Column("updated_by_user_id")
    public String updatedByUserId;

    public EntityVersionRecord toDomain() {
        return new EntityVersionRecord(
                tenantId, aggregateType, aggregateId,
                version, DeltaOperation.valueOf(operation),
                payloadJson, updatedAt, updatedByUserId);
    }

    public static EntityVersionEntity fromDomain(EntityVersionRecord record) {
        var e = new EntityVersionEntity();
        e.tenantId = record.tenantId();
        e.aggregateType = record.aggregateType();
        e.aggregateId = record.aggregateId();
        e.version = record.version();
        e.operation = record.operation().name();
        e.payloadJson = record.payloadJson();
        e.updatedAt = record.updatedAt();
        e.updatedByUserId = record.updatedByUserId();
        return e;
    }
}
