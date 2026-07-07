package com.yowyob.tiibntick.core.realtime.domain.model.enums;

/**
 * Classification of a geofence zone by its business purpose.
 *
 * <p>This enum is consumed by both the realtime engine (for zone transition events)
 * and {@code tnt-incident-core} which filters on {@code zoneType} to determine
 * whether to auto-create an incident when a deliverer enters the zone:</p>
 * <ul>
 *   <li>{@link #DANGER_ZONE} → auto-creates a {@code GEOGRAPHIC} incident of type
 *       {@code ZONE_DANGER} when entered.</li>
 *   <li>{@link #RESTRICTED_ZONE} → auto-creates a {@code GEOGRAPHIC} incident of type
 *       {@code ACCESS_RESTRICTED_AREA} when entered.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public enum GeofenceZoneType {

    /** Zone around a relay hub (TiiBnTick Point). Triggers hub-deposit flow start. */
    RELAY_HUB,

    /** Agency headquarters or branch office zone. */
    AGENCY_OFFICE,

    /** Geographic service area boundary for a tenant. */
    SERVICE_AREA,

    /** Customer delivery address zone (last-mile proximity alert). */
    DELIVERY_ADDRESS,

    /**
     * Zone flagged as dangerous for deliverers (armed conflict area, unsafe neighbourhood,
     * known accident hotspot, area under natural disaster, flood zone, etc.).
     * <br>
     * When a deliverer enters this zone, {@code tnt-incident-core} automatically creates
     * a {@code GEOGRAPHIC} incident of sub-type {@code ZONE_DANGER}.
     * The deliverer is advised to leave the zone immediately and contact the agency.
     */
    DANGER_ZONE,

    /**
     * Zone where delivery operations are prohibited (military perimeter, private property,
     * government restricted area, curfew zone).
     * <br>
     * When a deliverer enters this zone, {@code tnt-incident-core} automatically creates
     * a {@code GEOGRAPHIC} incident of sub-type {@code ACCESS_RESTRICTED_AREA}.
     */
    RESTRICTED_ZONE,

    /** Custom business zone defined by the tenant administrator. */
    CUSTOM
}
