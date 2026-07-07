package com.yowyob.tiibntick.core.resource.domain.model;

/**
 * Value Object representing the physical capacity of a vehicle.
 * Immutable; validation is enforced in the compact constructor.
 *
 * @author MANFOUO Braun.
 */
public record VehicleCapacity(double maxWeightKg, double maxVolumeM3) {

    public VehicleCapacity {
        if (maxWeightKg <= 0) {
            throw new IllegalArgumentException("maxWeightKg must be positive, got: " + maxWeightKg);
        }
        if (maxVolumeM3 <= 0) {
            throw new IllegalArgumentException("maxVolumeM3 must be positive, got: " + maxVolumeM3);
        }
    }

    /**
     * Checks whether this vehicle can physically carry a load defined by weight and volume.
     *
     * @param weightKg  load weight in kilograms
     * @param volumeM3  load volume in cubic metres
     * @return true if both constraints are satisfied
     */
    public boolean canCarry(double weightKg, double volumeM3) {
        return weightKg <= maxWeightKg && volumeM3 <= maxVolumeM3;
    }

    /**
     * Returns remaining weight capacity after deducting a given load.
     */
    public double remainingWeightKg(double usedWeightKg) {
        return maxWeightKg - usedWeightKg;
    }

    /**
     * Returns remaining volume capacity after deducting a given load.
     */
    public double remainingVolumeM3(double usedVolumeM3) {
        return maxVolumeM3 - usedVolumeM3;
    }
}
