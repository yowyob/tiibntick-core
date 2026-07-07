package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.FuelType;
import com.yowyob.tiibntick.core.resource.domain.model.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Command to add a new vehicle to a FreelancerOrganization's personal fleet.
 *
 * <p>The FreelancerOrg fleet is capped at {@code FreelancerVehicle.MAX_VEHICLES_PER_ORG} (3)
 * vehicles. The application service enforces this constraint.
 *
 * @author MANFOUO Braun
 */
public record AddFreelancerVehicleCommand(
        /** UUID of the owning FreelancerOrganization. Integration key — no join. */
        @NotNull UUID ownerOrgId,

        /**
         * Type of vehicle (MOTO, VELO, VOITURE, CAMIONNETTE, VELO_CARGO).
         * Freelancer-specific types are preferred over legacy Agency types.
         */
        @NotNull VehicleType type,

        /** Vehicle manufacturer brand (e.g., Honda, Toyota). */
        String brand,

        /** Vehicle model name (e.g., CB 125, Camry). */
        String model,

        /** Official plate or registration number. Must be unique per org. */
        @NotBlank String plateNumber,

        /** Maximum payload capacity in kilograms. */
        @Positive double maxCapacityKg,

        /** Cargo volume in m³ (nullable for motorbikes/bicycles). */
        Double volumeM3,

        /** Fuel type (ESSENCE, DIESEL, ELECTRIQUE, HYBRIDE). */
        FuelType fuelType,

        /**
         * Fuel consumption in liters per 100 km.
         * Used by tnt-billing-cost to compute operational mission cost.
         * Null for electric vehicles.
         */
        Double fuelConsumptionLPer100km,

        /** tnt-media-core reference for the vehicle registration document (carte grise). */
        String registrationDocRef,

        /** tnt-media-core reference for the vehicle insurance document. */
        String insuranceDocRef
) {}
