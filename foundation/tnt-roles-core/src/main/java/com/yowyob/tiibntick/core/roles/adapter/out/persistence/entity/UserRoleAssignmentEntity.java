package com.yowyob.tiibntick.core.roles.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for the {@code tnt_user_role_assignments} table. Mapped
 * to/from {@link com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment} by
 * {@code RolePersistenceMapper}.
 *
 * <p>Implements {@link Persistable} for the same reason as {@code RoleEntity} — the
 * {@code id} primary key is application-assigned, not DB-generated.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_user_role_assignments")
public class UserRoleAssignmentEntity implements Persistable<UUID> {

    @Id
    @Column("id")
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("user_id")
    private UUID userId;

    @Column("role_id")
    private UUID roleId;

    @Column("scope_type")
    private String scopeType;

    @Column("scope_id")
    private UUID scopeId;

    @Column("kernel_assignment_id")
    private UUID kernelAssignmentId;

    @Column("created_at")
    private Instant createdAt;

    public UserRoleAssignmentEntity() {
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

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public UUID getScopeId() {
        return scopeId;
    }

    public void setScopeId(UUID scopeId) {
        this.scopeId = scopeId;
    }

    public UUID getKernelAssignmentId() {
        return kernelAssignmentId;
    }

    public void setKernelAssignmentId(UUID kernelAssignmentId) {
        this.kernelAssignmentId = kernelAssignmentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
