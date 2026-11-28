package com.yowyob.tiibntick.core.delivery.domain.model.enums;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Correspondence between the logistics vehicle vocabulary of delivery-core ({@link LogisticsType})
 * and the vehicle type names of tnt-resource-core ({@code com.yowyob.tiibntick.core.resource.domain.model.VehicleType}).
 *
 * <p>Resource names are handled as strings so that neither module depends on the other.
 * {@link LogisticsType#SCOOTER} and {@link LogisticsType#BUS} have no resource-core counterpart today:
 * {@link #toResourceVehicleType} returns empty for them instead of guessing a neighbour.
 */
public final class LogisticsVehicleTypeMapping {

    private static final Map<LogisticsType, String> TO_RESOURCE = new EnumMap<>(LogisticsType.class);
    private static final Map<String, LogisticsType> FROM_RESOURCE = Map.ofEntries(
            Map.entry("BICYCLE", LogisticsType.BIKE),
            Map.entry("VELO", LogisticsType.BIKE),
            Map.entry("VELO_CARGO", LogisticsType.BIKE),
            Map.entry("MOTORCYCLE", LogisticsType.MOTORBIKE),
            Map.entry("MOTO", LogisticsType.MOTORBIKE),
            Map.entry("CAR", LogisticsType.CAR),
            Map.entry("VOITURE", LogisticsType.CAR),
            Map.entry("TRICYCLE", LogisticsType.TRICYCLE),
            Map.entry("VAN", LogisticsType.VAN),
            Map.entry("CAMIONNETTE", LogisticsType.VAN),
            Map.entry("TRUCK", LogisticsType.TRUCK));

    static {
        TO_RESOURCE.put(LogisticsType.BIKE, "BICYCLE");
        TO_RESOURCE.put(LogisticsType.MOTORBIKE, "MOTORCYCLE");
        TO_RESOURCE.put(LogisticsType.CAR, "CAR");
        TO_RESOURCE.put(LogisticsType.VAN, "VAN");
        TO_RESOURCE.put(LogisticsType.TRUCK, "TRUCK");
        TO_RESOURCE.put(LogisticsType.TRICYCLE, "TRICYCLE");
    }

    private LogisticsVehicleTypeMapping() {
    }

    /** The resource-core vehicle type name for {@code type}, or empty when resource-core has none. */
    public static Optional<String> toResourceVehicleType(LogisticsType type) {
        return Optional.ofNullable(type == null ? null : TO_RESOURCE.get(type));
    }

    /** The logistics type for a resource-core vehicle type name (case-insensitive), or empty if unknown. */
    public static Optional<LogisticsType> fromResourceVehicleType(String resourceTypeName) {
        if (resourceTypeName == null || resourceTypeName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(FROM_RESOURCE.get(resourceTypeName.trim().toUpperCase(Locale.ROOT)));
    }
}
