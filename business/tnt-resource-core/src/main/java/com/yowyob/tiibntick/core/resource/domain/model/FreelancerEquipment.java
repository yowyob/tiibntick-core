package com.yowyob.tiibntick.core.resource.domain.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing specialized physical equipment owned by a {@code FreelancerOrganization}.
 *
 * <p><b>Kernel extension principle:</b> References {@code ownerOrgId} (FreelancerOrganization UUID
 * from {@code tnt-organization-core}) as a pure UUID integration key — no class inheritance,
 * no physical FK across module boundaries.
 *
 * <p>FreelancerEquipment covers specialized physical tools that enhance delivery capabilities
 * (refrigerated boxes, cargo bags, waterproof covers, etc.) as opposed to the standard
 * operational {@link Equipment} (tablets, QR scanners) managed at branch level.
 *
 * <p>Equipment can be checked before mission assignment to verify capacity for special packages:
 * e.g., checking {@code hasRefrigeratedBox} for perishable deliveries.
 *
 * @author MANFOUO Braun
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE)
public final class FreelancerEquipment {

    private final UUID equipmentId;

    /**
     * UUID of the owning {@code FreelancerOrganization}.
     * Pure integration key — no physical FK (cross-module boundary).
     */
    private final UUID ownerOrgId;

    private final EquipmentType type;
    private final String description;

    /** Maximum payload capacity of this equipment in kg (nullable for non-carrying equipment). */
    private final Double maxCapacityKg;

    /** Whether the equipment is owned or rented by the FreelancerOrg. */
    private final OwnershipType ownedOrRented;

    /** Whether this equipment is currently active and usable. */
    private final boolean active;

    /**
     * ID of the mission this equipment is currently assigned to.
     * Null when the equipment is available for the next mission.
     */
    private final String currentlyAssignedMissionId;

    private final Instant createdAt;
    private final Instant updatedAt;

    private FreelancerEquipment(UUID equipmentId, UUID ownerOrgId, EquipmentType type,
            String description, Double maxCapacityKg, OwnershipType ownedOrRented,
            boolean active, String currentlyAssignedMissionId,
            Instant createdAt, Instant updatedAt) {
        this.equipmentId = Objects.requireNonNull(equipmentId, "equipmentId is required");
        this.ownerOrgId = Objects.requireNonNull(ownerOrgId, "ownerOrgId is required");
        this.type = Objects.requireNonNull(type, "type is required");
        this.description = description;
        this.maxCapacityKg = maxCapacityKg;
        this.ownedOrRented = ownedOrRented != null ? ownedOrRented : OwnershipType.OWNED;
        this.active = active;
        this.currentlyAssignedMissionId = currentlyAssignedMissionId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    // ── Factory methods ───────────────────────────────────────────────────

    /**
     * Registers a new piece of equipment in the FreelancerOrg's inventory.
     *
     * @param ownerOrgId     UUID of the owning FreelancerOrganization
     * @param type           equipment type (REFRIGERATED_BOX, CARGO_BAG, etc.)
     * @param description    free-text description
     * @param maxCapacityKg  capacity in kg (null for non-carrying equipment)
     * @param ownedOrRented  ownership type
     * @return new active FreelancerEquipment
     */
    public static FreelancerEquipment register(UUID ownerOrgId, EquipmentType type,
            String description, Double maxCapacityKg, OwnershipType ownedOrRented) {
        Instant now = Instant.now();
        return new FreelancerEquipment(UUID.randomUUID(), ownerOrgId, type, description,
                maxCapacityKg, ownedOrRented, true, null, now, now);
    }

    /**
     * Rehydrates a {@code FreelancerEquipment} from persistence.
     */
    public static FreelancerEquipment rehydrate(UUID equipmentId, UUID ownerOrgId, EquipmentType type,
            String description, Double maxCapacityKg, OwnershipType ownedOrRented,
            boolean active, String currentlyAssignedMissionId,
            Instant createdAt, Instant updatedAt) {
        return new FreelancerEquipment(equipmentId, ownerOrgId, type, description,
                maxCapacityKg, ownedOrRented, active, currentlyAssignedMissionId,
                createdAt, updatedAt);
    }

    // ── Domain behaviour ──────────────────────────────────────────────────

    /**
     * Assigns this equipment to an active mission.
     *
     * @param missionId the mission ID to assign to
     * @return new FreelancerEquipment with currentlyAssignedMissionId set
     * @throws IllegalStateException if the equipment is not available
     */
    public FreelancerEquipment assignToMission(String missionId) {
        Objects.requireNonNull(missionId, "missionId is required");
        if (!isAvailable()) {
            throw new IllegalStateException(
                    "Equipment " + equipmentId + " is not available. missionId=" + currentlyAssignedMissionId);
        }
        return new FreelancerEquipment(equipmentId, ownerOrgId, type, description,
                maxCapacityKg, ownedOrRented, active, missionId, createdAt, Instant.now());
    }

    /**
     * Releases this equipment from its current mission.
     *
     * @return new FreelancerEquipment with currentlyAssignedMissionId cleared
     */
    public FreelancerEquipment releaseFromMission() {
        return new FreelancerEquipment(equipmentId, ownerOrgId, type, description,
                maxCapacityKg, ownedOrRented, active, null, createdAt, Instant.now());
    }

    /**
     * Deactivates this equipment (broken, retired, sold).
     *
     * @return new deactivated FreelancerEquipment
     */
    public FreelancerEquipment deactivate() {
        return new FreelancerEquipment(equipmentId, ownerOrgId, type, description,
                maxCapacityKg, ownedOrRented, false, null, createdAt, Instant.now());
    }

    // ── Query methods ─────────────────────────────────────────────────────

    /** Returns true if this equipment is active and not on a mission. */
    public boolean isAvailable() {
        return active && currentlyAssignedMissionId == null;
    }

    /** Returns true if this is a refrigerated box (used for hasRefrigeratedBox DSL variable). */
    public boolean isRefrigeratedBox() {
        return type == EquipmentType.REFRIGERATED_BOX;
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public UUID equipmentId() { return equipmentId; }
    public UUID ownerOrgId() { return ownerOrgId; }
    public EquipmentType type() { return type; }
    public String description() { return description; }
    public Double maxCapacityKg() { return maxCapacityKg; }
    public OwnershipType ownedOrRented() { return ownedOrRented; }
    public boolean isActive() { return active; }
    public String currentlyAssignedMissionId() { return currentlyAssignedMissionId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
