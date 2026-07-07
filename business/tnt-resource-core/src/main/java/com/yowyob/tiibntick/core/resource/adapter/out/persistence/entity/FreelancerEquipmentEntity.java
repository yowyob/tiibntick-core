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
 * R2DBC persistence entity for the FreelancerEquipment domain entity.
 * Maps to the {@code tnt_freelancer_equipments} table.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_freelancer_equipments")
public class FreelancerEquipmentEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;
    @Column("freelancer_org_id")              private UUID freelancerOrgId;
    @Column("equipment_type")                 private String equipmentType;
    @Column("description")                    private String description;
    @Column("max_capacity_kg")                private Double maxCapacityKg;
    @Column("ownership_type")                 private String ownershipType;
    @Column("is_active")                      private boolean active;
    @Column("currently_assigned_mission_id")  private String currentlyAssignedMissionId;
    @Column("created_at")                     private Instant createdAt;
    @Column("updated_at")                     private Instant updatedAt;

    public static FreelancerEquipmentEntity fromDomain(FreelancerEquipment eq) {
        FreelancerEquipmentEntity e = new FreelancerEquipmentEntity();
        e.id = eq.equipmentId();
        e.freelancerOrgId = eq.ownerOrgId();
        e.equipmentType = eq.type().name();
        e.description = eq.description();
        e.maxCapacityKg = eq.maxCapacityKg();
        e.ownershipType = eq.ownedOrRented() != null ? eq.ownedOrRented().name() : OwnershipType.OWNED.name();
        e.active = eq.isActive();
        e.currentlyAssignedMissionId = eq.currentlyAssignedMissionId();
        e.createdAt = eq.createdAt();
        e.updatedAt = eq.updatedAt();
        return e;
    }

    public FreelancerEquipment toDomain() {
        return FreelancerEquipment.rehydrate(
                id, freelancerOrgId, EquipmentType.valueOf(equipmentType),
                description, maxCapacityKg,
                ownershipType != null ? OwnershipType.valueOf(ownershipType) : OwnershipType.OWNED,
                active, currentlyAssignedMissionId, createdAt, updatedAt);
    }

    // --- Getters & Setters ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
    public UUID getFreelancerOrgId() { return freelancerOrgId; }
    public void setFreelancerOrgId(UUID v) { this.freelancerOrgId = v; }
    public String getEquipmentType() { return equipmentType; }
    public void setEquipmentType(String v) { this.equipmentType = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public Double getMaxCapacityKg() { return maxCapacityKg; }
    public void setMaxCapacityKg(Double v) { this.maxCapacityKg = v; }
    public String getOwnershipType() { return ownershipType; }
    public void setOwnershipType(String v) { this.ownershipType = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getCurrentlyAssignedMissionId() { return currentlyAssignedMissionId; }
    public void setCurrentlyAssignedMissionId(String v) { this.currentlyAssignedMissionId = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
