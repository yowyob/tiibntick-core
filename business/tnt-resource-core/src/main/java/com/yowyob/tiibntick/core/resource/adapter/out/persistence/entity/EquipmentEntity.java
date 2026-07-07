package com.yowyob.tiibntick.core.resource.adapter.out.persistence.entity;

import com.yowyob.tiibntick.core.resource.domain.model.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for Equipment.
 * Maps to the {@code tnt_equipment} table.
 *
 * @author MANFOUO Braun.
 */
@Table("tnt_equipment")
public class EquipmentEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    @Transient
    private boolean newEntity;
    @Column("tenant_id")       private UUID tenantId;
    @Column("organization_id") private UUID organizationId;
    @Column("branch_id")       private UUID branchId;
    @Column("type")            private String type;
    @Column("serial_number")   private String serialNumber;
    @Column("description")     private String description;
    @Column("status")          private String status;
    @Column("assigned_user_id") private UUID assignedUserId;
    @Column("purchased_at")    private java.time.LocalDate purchasedAt;
    @Column("warranty_expires_at") private java.time.LocalDate warrantyExpiresAt;
    @Column("created_at")      private Instant createdAt;
    @Column("updated_at")      private Instant updatedAt;

    @Override
    public boolean isNew() { return newEntity; }
    public void setNew(boolean newEntity) { this.newEntity = newEntity; }

    public static EquipmentEntity fromDomain(Equipment equipment) {
        EquipmentEntity e = new EquipmentEntity();
        e.newEntity = equipment.createdAt().equals(equipment.updatedAt());
        e.id = equipment.id();
        e.tenantId = equipment.tenantId();
        e.organizationId = equipment.organizationId();
        e.branchId = equipment.branchId();
        e.type = equipment.type().name();
        e.serialNumber = equipment.serialNumber();
        e.description = equipment.description();
        e.status = equipment.status().name();
        e.assignedUserId = equipment.assignedUserId();
        e.purchasedAt = equipment.purchasedAt();
        e.warrantyExpiresAt = equipment.warrantyExpiresAt();
        e.createdAt = equipment.createdAt();
        e.updatedAt = equipment.updatedAt();
        return e;
    }

    public Equipment toDomain() {
        return Equipment.rehydrate(id, tenantId, organizationId, branchId,
                EquipmentType.valueOf(type), serialNumber, description,
                EquipmentStatus.valueOf(status), assignedUserId, purchasedAt,
                warrantyExpiresAt, createdAt, updatedAt);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getAssignedUserId() { return assignedUserId; }
    public void setAssignedUserId(UUID assignedUserId) { this.assignedUserId = assignedUserId; }
    public java.time.LocalDate getPurchasedAt() { return purchasedAt; }
    public void setPurchasedAt(java.time.LocalDate purchasedAt) { this.purchasedAt = purchasedAt; }
    public java.time.LocalDate getWarrantyExpiresAt() { return warrantyExpiresAt; }
    public void setWarrantyExpiresAt(java.time.LocalDate warrantyExpiresAt) { this.warrantyExpiresAt = warrantyExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
