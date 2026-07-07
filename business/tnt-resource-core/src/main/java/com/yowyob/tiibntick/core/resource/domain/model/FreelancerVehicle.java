package com.yowyob.tiibntick.core.resource.domain.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a vehicle belonging to a {@code FreelancerOrganization}.
 *
 * <p><b>Kernel extension principle:</b> This entity does NOT extend any kernel Vehicle or
 * Actor class. It references {@code ownerOrgId} (the FreelancerOrganization UUID from
 * {@code tnt-organization-core}) as a pure integration key (UUID reference), following the
 * TiiBnTick extension-without-repetition pattern.
 *
 * <p><b>Distinction from Agency {@link Vehicle}:</b>
 * <ul>
 *   <li>{@link Vehicle} belongs to an Agency (managed fleet with maintenance records).</li>
 *   <li>{@code FreelancerVehicle} belongs to a FreelancerOrg (personal vehicle, 1–3 max).</li>
 *   <li>{@code FreelancerVehicle} tracks {@code fuelConsumptionLPer100km} for cost calculation.</li>
 *   <li>Missions link to {@code currentMissionId} instead of using ResourceAllocation.</li>
 * </ul>
 *
 * <p>A FreelancerOrg can have at most <b>3 vehicles</b> (enforced by
 * {@code FreelancerFleetApplicationService}).
 *
 * @author MANFOUO Braun
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE)
public final class FreelancerVehicle {

    private final UUID vehicleId;

    /**
     * UUID of the owning {@code FreelancerOrganization}.
     * Pure integration key — no physical FK to tnt-organization-core (cross-module boundary).
     */
    private final UUID ownerOrgId;

    private final VehicleType type;
    private final String brand;
    private final String model;
    private final String plateNumber;
    private final double maxCapacityKg;
    private final Double volumeM3;
    private final FuelType fuelType;

    /**
     * Fuel consumption in liters per 100 km.
     * Used by {@code tnt-billing-cost}'s {@code FleetCostParameters} to compute
     * the operational cost of a delivery mission.
     */
    private final Double fuelConsumptionLPer100km;

    /**
     * Reference to the registration document stored in {@code tnt-media-core}.
     * Stored as a URI/URL string to avoid coupling with the media module.
     */
    private final String registrationDocRef;

    /**
     * Reference to the insurance document stored in {@code tnt-media-core}.
     */
    private final String insuranceDocRef;

    /** Whether this vehicle is currently active and usable for missions. */
    private final boolean active;

    /** Date of last maintenance check (nullable if never maintained). */
    private final LocalDate lastMaintenanceAt;

    /**
     * ID of the currently active mission this vehicle is assigned to.
     * Null when the vehicle is available. Set by {@code assignToMission()}.
     */
    private final String currentMissionId;

    private final Instant createdAt;
    private final Instant updatedAt;

    // ── Max fleet size per FreelancerOrg ──────────────────────────────────
    public static final int MAX_VEHICLES_PER_ORG = 3;

    private FreelancerVehicle(UUID vehicleId, UUID ownerOrgId, VehicleType type,
            String brand, String model, String plateNumber, double maxCapacityKg,
            Double volumeM3, FuelType fuelType, Double fuelConsumptionLPer100km,
            String registrationDocRef, String insuranceDocRef, boolean active,
            LocalDate lastMaintenanceAt, String currentMissionId,
            Instant createdAt, Instant updatedAt) {
        this.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId is required");
        this.ownerOrgId = Objects.requireNonNull(ownerOrgId, "ownerOrgId is required");
        this.type = Objects.requireNonNull(type, "type is required");
        this.brand = brand;
        this.model = model;
        if (plateNumber == null || plateNumber.isBlank()) {
            throw new IllegalArgumentException("plateNumber must not be blank");
        }
        this.plateNumber = plateNumber.toUpperCase().trim();
        if (maxCapacityKg <= 0) {
            throw new IllegalArgumentException("maxCapacityKg must be > 0, got: " + maxCapacityKg);
        }
        this.maxCapacityKg = maxCapacityKg;
        this.volumeM3 = volumeM3;
        this.fuelType = fuelType;
        this.fuelConsumptionLPer100km = fuelConsumptionLPer100km;
        this.registrationDocRef = registrationDocRef;
        this.insuranceDocRef = insuranceDocRef;
        this.active = active;
        this.lastMaintenanceAt = lastMaintenanceAt;
        this.currentMissionId = currentMissionId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    // ── Factory: add a new vehicle to a FreelancerOrg fleet ───────────────

    /**
     * Registers a new vehicle in the FreelancerOrg's personal fleet.
     *
     * @param ownerOrgId              UUID of the owning FreelancerOrganization
     * @param type                    vehicle type (MOTO, VELO, VOITURE, CAMIONNETTE, VELO_CARGO)
     * @param brand                   manufacturer brand
     * @param model                   vehicle model name
     * @param plateNumber             official plate/registration number
     * @param maxCapacityKg           maximum payload capacity in kg
     * @param volumeM3                cargo volume in m³ (nullable)
     * @param fuelType                fuel type (ESSENCE, DIESEL, ELECTRIQUE, HYBRIDE)
     * @param fuelConsumptionLPer100km fuel consumption in L/100km (nullable)
     * @param registrationDocRef      media-core reference for registration document
     * @param insuranceDocRef         media-core reference for insurance document
     * @return new active FreelancerVehicle
     */
    public static FreelancerVehicle register(UUID ownerOrgId, VehicleType type,
            String brand, String model, String plateNumber, double maxCapacityKg,
            Double volumeM3, FuelType fuelType, Double fuelConsumptionLPer100km,
            String registrationDocRef, String insuranceDocRef) {
        Instant now = Instant.now();
        return new FreelancerVehicle(UUID.randomUUID(), ownerOrgId, type, brand, model,
                plateNumber, maxCapacityKg, volumeM3, fuelType, fuelConsumptionLPer100km,
                registrationDocRef, insuranceDocRef, true, null, null, now, now);
    }

    /**
     * Rehydrates a {@code FreelancerVehicle} from persistence.
     */
    public static FreelancerVehicle rehydrate(UUID vehicleId, UUID ownerOrgId, VehicleType type,
            String brand, String model, String plateNumber, double maxCapacityKg,
            Double volumeM3, FuelType fuelType, Double fuelConsumptionLPer100km,
            String registrationDocRef, String insuranceDocRef, boolean active,
            LocalDate lastMaintenanceAt, String currentMissionId,
            Instant createdAt, Instant updatedAt) {
        return new FreelancerVehicle(vehicleId, ownerOrgId, type, brand, model, plateNumber,
                maxCapacityKg, volumeM3, fuelType, fuelConsumptionLPer100km,
                registrationDocRef, insuranceDocRef, active, lastMaintenanceAt,
                currentMissionId, createdAt, updatedAt);
    }

    // ── Domain behaviour ──────────────────────────────────────────────────

    /**
     * Assigns this vehicle to an active mission.
     *
     * @param missionId the mission ID to assign to
     * @return new FreelancerVehicle instance with currentMissionId set
     * @throws IllegalStateException if the vehicle is not available (already on a mission)
     */
    public FreelancerVehicle assignToMission(String missionId) {
        Objects.requireNonNull(missionId, "missionId is required");
        if (!isAvailable()) {
            throw new IllegalStateException(
                    "Vehicle " + vehicleId + " is not available. currentMissionId=" + currentMissionId);
        }
        return new FreelancerVehicle(vehicleId, ownerOrgId, type, brand, model, plateNumber,
                maxCapacityKg, volumeM3, fuelType, fuelConsumptionLPer100km,
                registrationDocRef, insuranceDocRef, active, lastMaintenanceAt,
                missionId, createdAt, Instant.now());
    }

    /**
     * Releases this vehicle from its current mission, making it available again.
     *
     * @return new FreelancerVehicle instance with currentMissionId cleared
     * @throws IllegalStateException if the vehicle is not on a mission
     */
    public FreelancerVehicle releaseFromMission() {
        if (currentMissionId == null) {
            throw new IllegalStateException(
                    "Vehicle " + vehicleId + " is not assigned to any mission");
        }
        return new FreelancerVehicle(vehicleId, ownerOrgId, type, brand, model, plateNumber,
                maxCapacityKg, volumeM3, fuelType, fuelConsumptionLPer100km,
                registrationDocRef, insuranceDocRef, active, lastMaintenanceAt,
                null, createdAt, Instant.now());
    }

    /**
     * Deactivates this vehicle (e.g., sold, scrapped, long-term storage).
     * Deactivated vehicles are excluded from mission matching.
     *
     * @return new deactivated FreelancerVehicle
     * @throws IllegalStateException if the vehicle is currently on a mission
     */
    public FreelancerVehicle deactivate() {
        if (currentMissionId != null) {
            throw new IllegalStateException(
                    "Cannot deactivate vehicle " + vehicleId + " — currently on mission: " + currentMissionId);
        }
        return new FreelancerVehicle(vehicleId, ownerOrgId, type, brand, model, plateNumber,
                maxCapacityKg, volumeM3, fuelType, fuelConsumptionLPer100km,
                registrationDocRef, insuranceDocRef, false, lastMaintenanceAt,
                null, createdAt, Instant.now());
    }

    /**
     * Records a maintenance event, updating the lastMaintenanceAt date.
     *
     * @param maintenanceDate the date maintenance was performed
     * @return new FreelancerVehicle with updated maintenance date
     */
    public FreelancerVehicle recordMaintenance(LocalDate maintenanceDate) {
        Objects.requireNonNull(maintenanceDate, "maintenanceDate is required");
        return new FreelancerVehicle(vehicleId, ownerOrgId, type, brand, model, plateNumber,
                maxCapacityKg, volumeM3, fuelType, fuelConsumptionLPer100km,
                registrationDocRef, insuranceDocRef, active, maintenanceDate,
                currentMissionId, createdAt, Instant.now());
    }

    /**
     * Updates the document references (e.g., after KYC document upload).
     */
    public FreelancerVehicle updateDocs(String registrationDocRef, String insuranceDocRef) {
        return new FreelancerVehicle(vehicleId, ownerOrgId, type, brand, model, plateNumber,
                maxCapacityKg, volumeM3, fuelType, fuelConsumptionLPer100km,
                registrationDocRef, insuranceDocRef, active, lastMaintenanceAt,
                currentMissionId, createdAt, Instant.now());
    }

    // ── Query methods ─────────────────────────────────────────────────────

    /** Returns true if this vehicle is active and not currently assigned to any mission. */
    public boolean isAvailable() {
        return active && currentMissionId == null;
    }

    /**
     * Returns true if this vehicle can carry a parcel with the given weight and volume.
     *
     * @param weightKg  required weight capacity in kg
     * @param volumeM3  required volume in m³ (0 = no volume constraint)
     */
    public boolean canCarry(double weightKg, double volumeM3) {
        if (weightKg > this.maxCapacityKg) return false;
        if (volumeM3 > 0 && this.volumeM3 != null && volumeM3 > this.volumeM3) return false;
        return true;
    }

    /**
     * Computes the estimated fuel cost for a given distance in XAF.
     *
     * @param distanceKm      delivery distance in km
     * @param fuelPricePerLiter fuel price per liter in XAF
     * @return estimated fuel cost in XAF (0 if fuel consumption data not available)
     */
    public double estimateFuelCostXaf(double distanceKm, double fuelPricePerLiter) {
        if (fuelConsumptionLPer100km == null || fuelType == FuelType.ELECTRIQUE) {
            return 0.0;
        }
        return (distanceKm / 100.0) * fuelConsumptionLPer100km * fuelPricePerLiter;
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public UUID vehicleId() { return vehicleId; }
    public UUID ownerOrgId() { return ownerOrgId; }
    public VehicleType type() { return type; }
    public String brand() { return brand; }
    public String model() { return model; }
    public String plateNumber() { return plateNumber; }
    public double maxCapacityKg() { return maxCapacityKg; }
    public Double volumeM3() { return volumeM3; }
    public FuelType fuelType() { return fuelType; }
    public Double fuelConsumptionLPer100km() { return fuelConsumptionLPer100km; }
    public String registrationDocRef() { return registrationDocRef; }
    public String insuranceDocRef() { return insuranceDocRef; }
    public boolean isActive() { return active; }
    public LocalDate lastMaintenanceAt() { return lastMaintenanceAt; }
    public String currentMissionId() { return currentMissionId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
