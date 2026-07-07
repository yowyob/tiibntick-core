package com.yowyob.kernel.event.adapter.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * R2DBC entity mapped to the {@code event_bus.event_schemas} table in
 * {@code yow_kernel_db}.
 *
 * <p>Each row represents one Avro schema version for a given event type and
 * Yowyob solution. The unique constraint
 * {@code (event_type, solution_code, version)} ensures that schema versions are
 * monotonically increasing and never overwritten.
 *
 * <p>Table DDL (managed by Liquibase — see {@code 002-create-tables.yaml}):
 * <pre>{@code
 * CREATE TABLE event_bus.event_schemas (
 *     id                    VARCHAR(36)  PRIMARY KEY,
 *     event_type            VARCHAR(200) NOT NULL,
 *     version               INTEGER      NOT NULL,
 *     solution_code         VARCHAR(10)  NOT NULL,
 *     avro_schema_json      TEXT         NOT NULL,
 *     compatibility         VARCHAR(30)  NOT NULL DEFAULT 'FULL',
 *     is_backward_compatible BOOLEAN     NOT NULL DEFAULT TRUE,
 *     registered_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
 *     deprecated_at         TIMESTAMPTZ,
 *     CONSTRAINT uq_event_schemas_type_solution_version
 *         UNIQUE (event_type, solution_code, version)
 * );
 * }</pre>
 */
@Table("event_bus.event_schemas")
public class EventSchemaEntity {

    @Id
    private String id;

    @Column("event_type")
    private String eventType;

    @Column("version")
    private int version;

    @Column("solution_code")
    private String solutionCode;

    @Column("avro_schema_json")
    private String avroSchemaJson;

    @Column("compatibility")
    private String compatibility;

    @Column("is_backward_compatible")
    private boolean isBackwardCompatible;

    @Column("registered_at")
    private LocalDateTime registeredAt;

    @Column("deprecated_at")
    private LocalDateTime deprecatedAt;

    // ── No-arg constructor required by Spring Data R2DBC ────────────────────

    public EventSchemaEntity() {}

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getId()                              { return id; }
    public void   setId(String id)                     { this.id = id; }

    public String getEventType()                       { return eventType; }
    public void   setEventType(String v)               { this.eventType = v; }

    public int    getVersion()                         { return version; }
    public void   setVersion(int v)                    { this.version = v; }

    public String getSolutionCode()                    { return solutionCode; }
    public void   setSolutionCode(String v)            { this.solutionCode = v; }

    public String getAvroSchemaJson()                  { return avroSchemaJson; }
    public void   setAvroSchemaJson(String v)          { this.avroSchemaJson = v; }

    public String getCompatibility()                   { return compatibility; }
    public void   setCompatibility(String v)           { this.compatibility = v; }

    public boolean isBackwardCompatible()              { return isBackwardCompatible; }
    public void    setBackwardCompatible(boolean v)    { this.isBackwardCompatible = v; }

    public LocalDateTime getRegisteredAt()             { return registeredAt; }
    public void setRegisteredAt(LocalDateTime v)       { this.registeredAt = v; }

    public LocalDateTime getDeprecatedAt()             { return deprecatedAt; }
    public void setDeprecatedAt(LocalDateTime v)       { this.deprecatedAt = v; }
}
