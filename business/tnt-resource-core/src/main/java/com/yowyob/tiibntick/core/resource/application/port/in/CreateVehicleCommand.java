package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Command to register a new vehicle in the TiiBnTick Agency fleet.
 *
 * <p> — Added {@link #hasRefrigeration} to support cold-chain capability registration
 * at vehicle creation time (previously only set via direct entity construction).
 *
 * @author MANFOUO Braun
 */
public record CreateVehicleCommand(
        @NotNull UUID tenantId,
        @NotNull UUID organizationId,
        @NotNull UUID agencyId,
        @NotBlank String registrationNumber,
        @NotBlank String brand,
        @NotBlank String model,
        int yearOfManufacture,
        @NotNull VehicleType type,
        @Positive double maxWeightKg,
        @Positive double maxVolumeM3,

        /**
         * Whether this vehicle has a built-in cold chain / refrigeration system.
         * Defaults to {@code false} if not provided.
         * Used by {@code IVehicleCompatibilityPort} (tnt-incident-core) for perishable
         * delivery matching and by the billing engine ({@code PricingContext.requiresRefrigeration}).
         */
        boolean hasRefrigeration
) {
    /**
     * Convenience factory without refrigeration (backward compatible).
     */
    public static CreateVehicleCommand withoutRefrigeration(UUID tenantId, UUID organizationId,
            UUID agencyId, String registrationNumber, String brand, String model,
            int yearOfManufacture, VehicleType type, double maxWeightKg, double maxVolumeM3) {
        return new CreateVehicleCommand(tenantId, organizationId, agencyId, registrationNumber,
                brand, model, yearOfManufacture, type, maxWeightKg, maxVolumeM3, false);
    }
}
