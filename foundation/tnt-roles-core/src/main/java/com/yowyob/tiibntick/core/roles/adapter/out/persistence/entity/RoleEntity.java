package com.yowyob.tiibntick.core.roles.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for the {@code tnt_roles} table. Mapped to/from
 * {@link com.yowyob.tiibntick.core.roles.domain.model.Role} by
 * {@code RolePersistenceMapper}.
 *
 * <p>Implements {@link Persistable} with an explicit, transient {@code isNew} flag —
 * the {@code id} primary key is application-assigned (a random UUID from
 * {@code Role.create}), never DB-generated. Without this, Spring Data R2DBC's default
 * new-vs-existing detection (based on whether the {@code @Id} field is {@code null|})
 * would treat every entity as "existing" and always issue an {@code UPDATE}, silently
 * no-op'ing on the first save of a brand-new row. Same pattern as
 * {@code tnt-organization-core}'s {@code AgencyEntity}.
 *
 * <p>{@code permissions} is persisted as a single comma-joined {@code TEXT} column
 * rather than a native Postgres {@code TEXT[]} array. This repo has no
 * {@code r2dbc-postgresql} array-codec registration anywhere ({@code ConnectionFactoryOptions}/
 * codec registrar), and the one existing precedent for a multi-value column mapped through
 * Spring Data R2DBC in this codebase ({@code tnt-route-core}'s {@code KalmanStateEntity},
 * a comma-joined covariance matrix) also avoids native array columns. Rather than rely on
 * unverified array-codec behavior for this Spring Boot/R2DBC version, this entity follows
 * the same proven, already-used-in-repo convention. See
 * {@code RolePersistenceMapper#splitPermissions}/{@code #joinPermissions} for the
 * conversion; permission codes never legitimately contain a comma.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_roles")
public class RoleEntity implements Persistable<UUID> {

    @Id
    @Column("id")
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("code")
    private String code;

    @Column("name")
    private String name;

    @Column("scope_type")
    private String scopeType;

    /** Comma-joined permission codes — see class Javadoc for why not {@code TEXT[]}. */
    @Column("permissions")
    private String permissions;

    @Column("system_role")
    private boolean systemRole;

    @Column("editable")
    private boolean editable;

    @Column("kernel_role_id")
    private UUID kernelRoleId;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    public RoleEntity() {
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    /** Marks this instance as a brand-new row — forces {@code INSERT} on save. */
    public void markNew() {
        this.isNew = true;
    }

    /** Marks this instance as an already-persisted row — forces {@code UPDATE} on save. */
    public void markNotNew() {
        this.isNew = false;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public void setSystemRole(boolean systemRole) {
        this.systemRole = systemRole;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    public UUID getKernelRoleId() {
        return kernelRoleId;
    }

    public void setKernelRoleId(UUID kernelRoleId) {
        this.kernelRoleId = kernelRoleId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
