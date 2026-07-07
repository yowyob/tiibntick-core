package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for the {@link com.yowyob.tiibntick.core.organization.domain.model.Agency} aggregate.
 *
 * <p>Maps to the {@code tnt_agency} table. All fields are value-typed — no JPA/Hibernate,
 * no lazy loading. This entity is purely a data carrier in the infrastructure adapter layer.
 *
 * <p>Key columns:
 * <ul>
 *   <li>{@code id}               — TiiBnTick internal primary key (UUID).</li>
 *   <li>{@code organization_id}  — Kernel integration key (RT-comops organization UUID). NOT NULL.</li>
 *   <li>{@code tenant_id}        — Multi-tenant isolation column. NOT NULL.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("tnt_agency")
public class AgencyEntity implements Persistable<UUID> {

    /** TiiBnTick internal agency primary key. */
    @Id
    @Column("id")
    private UUID id;

    @Transient
    private boolean isNew;

    /**
     * Kernel integration key.
     * References the Organization entity in RT-comops-organization-core.
     * Persisted as {@code organization_id UUID NOT NULL}.
     */
    @Column("organization_id")
    private UUID organizationId;

    /** Multi-tenant isolation column. */
    @Column("tenant_id")
    private UUID tenantId;

    /** Agency operating name. */
    @Column("name")
    private String name;

    /** National commerce registry number (e.g., RCCM in Cameroon). Nullable. */
    @Column("commerce_registry_number")
    private String commerceRegistryNumber;

    /**
     * Primary currency for this agency (ISO 4217 code).
     * Defaults to "XAF" (CFA Franc BEAC).
     */
    @Column("primary_currency")
    private String primaryCurrency;

    /** Record creation timestamp (UTC). */
    @Column("created_at")
    private Instant createdAt;

    /** Last modification timestamp (UTC). */
    @Column("updated_at")
    private Instant updatedAt;
}
