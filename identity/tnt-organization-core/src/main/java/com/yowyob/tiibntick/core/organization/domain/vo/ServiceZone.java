package com.yowyob.tiibntick.core.organization.domain.vo;

import com.yowyob.tiibntick.core.organization.domain.enums.DeliveryZoneType;
import com.yowyob.tiibntick.core.organization.domain.enums.ZoneAccessDifficulty;

/**
 * Value Object representing a polygonal geographic service coverage zone.
 *
 * <p>The polygon is stored as a WKT (Well-Known Text) string compatible with
 * PostGIS geometry functions (e.g., {@code ST_Within}, {@code ST_Intersects}).
 *
 * <p>Example WKT polygon:
 * <pre>{@code
 *     POLYGON((9.7 4.0, 9.8 4.0, 9.8 4.1, 9.7 4.1, 9.7 4.0))
 * }</pre>
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@code accessDifficulty} — road/access difficulty rating; used by the
 *       billing engine (remote-zone surcharge) and routing engine (ETA adjustment).
 *       Nullable — defaults to {@link ZoneAccessDifficulty#LOW} when absent.</li>
 *   <li>{@code zoneType} — urbanisation classification (URBAN, PERI_URBAN, RURAL…);
 *       used for vehicle selection and billing context.
 *       Nullable — defaults to {@link DeliveryZoneType#URBAN} when absent.</li>
 * </ul>
 *
 * @param zoneName          Human-readable name of the coverage zone (e.g., "Douala Akwa")
 * @param polygonBoundsWkt  WKT polygon defining geographic boundaries (SRID 4326)
 * @param active            Whether this zone is currently active and accepting deliveries
 * @param accessDifficulty  Road/access difficulty rating (nullable — defaults to LOW)
 * @param zoneType          Zone urbanisation type (nullable — defaults to URBAN)
 *
 * @author MANFOUO Braun
 */
public record ServiceZone(
        String zoneName,
        String polygonBoundsWkt,
        boolean active,
        ZoneAccessDifficulty accessDifficulty,
        DeliveryZoneType zoneType
) {

    /**
     * Compact constructor — validates required fields and applies safe defaults.
     *
     * @throws IllegalArgumentException if {@code zoneName} or {@code polygonBoundsWkt} is blank
     */
    public ServiceZone {
        if (zoneName == null || zoneName.isBlank()) {
            throw new IllegalArgumentException("ServiceZone name must not be blank");
        }
        if (polygonBoundsWkt == null || polygonBoundsWkt.isBlank()) {
            throw new IllegalArgumentException("ServiceZone polygon WKT must not be blank");
        }
        // Apply safe defaults for nullable classification fields
        if (accessDifficulty == null) {
            accessDifficulty = ZoneAccessDifficulty.LOW;
        }
        if (zoneType == null) {
            zoneType = DeliveryZoneType.URBAN;
        }
    }

    // ─── Backward-compatible factory methods ─────────────────────────────────

    /**
     * Creates an active service zone with default difficulty and type.
     * Backward-compatible factory kept for existing Branch usage.
     *
     * @param zoneName        the zone name
     * @param polygonBoundsWkt the WKT polygon
     * @return an active {@link ServiceZone} with {@code LOW} difficulty and {@code URBAN} type
     */
    public static ServiceZone active(String zoneName, String polygonBoundsWkt) {
        return new ServiceZone(zoneName, polygonBoundsWkt, true,
                ZoneAccessDifficulty.LOW, DeliveryZoneType.URBAN);
    }

    /**
     * Creates an active service zone with explicit access classification.
     *
     * @param zoneName         the zone name
     * @param polygonBoundsWkt the WKT polygon
     * @param difficulty       the access difficulty level
     * @param type             the zone urbanisation type
     * @return an active {@link ServiceZone} with the given classification
     */
    public static ServiceZone of(String zoneName, String polygonBoundsWkt,
                                  ZoneAccessDifficulty difficulty, DeliveryZoneType type) {
        return new ServiceZone(zoneName, polygonBoundsWkt, true, difficulty, type);
    }

    /**
     * Returns a deactivated copy of this zone.
     *
     * @return a new {@link ServiceZone} with {@code active = false}
     */
    public ServiceZone deactivate() {
        return new ServiceZone(zoneName, polygonBoundsWkt, false, accessDifficulty, zoneType);
    }
}
