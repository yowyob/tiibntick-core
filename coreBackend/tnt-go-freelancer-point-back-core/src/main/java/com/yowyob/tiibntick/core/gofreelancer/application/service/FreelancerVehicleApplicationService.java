package com.yowyob.tiibntick.core.gofreelancer.application.service;

import org.springframework.stereotype.Service;

/**
 * Vehicle helpers of the Go-Freelancer module.
 *
 * <p>The former {@code registerVehicle} (HTTP call to a non-existent {@code /api/v1/vehicles} route of
 * resource-core, falling back to a random UUID on any error) was never called and has been removed
 * (lot L3, de-duplication): no fictitious vehicle id is produced any more.
 */
@Service
public class FreelancerVehicleApplicationService {

    public double calculateVolumeInM3(Double length, Double width, Double height, String unit) {
        if (length == null || width == null || height == null) {
            return 0.0;
        }

        double volume = length * width * height;
        if (unit == null || unit.isBlank()) {
            return volume; // Fallback assumes meters
        }

        return switch (unit.trim().toLowerCase()) {
            case "cm" -> volume / 1_000_000.0;          // Centimeters
            case "mm" -> volume / 1_000_000_000.0;      // Millimeters
            case "m"  -> volume;                        // Meters
            case "in", "inch" -> volume * 0.000016387;  // Inches
            default -> volume;
        };
    }
}
