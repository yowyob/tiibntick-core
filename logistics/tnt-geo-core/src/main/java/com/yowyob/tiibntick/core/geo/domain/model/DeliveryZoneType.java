package com.yowyob.tiibntick.core.geo.domain.model;

/**
 * Classification of the delivery destination zone.
 *
 * <p>Used as a DSL variable in the TiiBnTick billing engine
 * ({@code deliveryZoneType}) to apply zone-specific pricing modifiers.
 * Computed by {@code IFreelancerOrgGeoUseCase.resolveDeliveryZoneType()}.
 *
 * <p>Zone classification for the Cameroonian logistics context:
 * <ul>
 *   <li>URBAN — city centre or major district (Yaoundé Centre, Douala Akwa)</li>
 *   <li>PERI_URBAN — city outskirts, banlieue, growing districts</li>
 *   <li>RURAL — small towns, village areas, agricultural zones</li>
 *   <li>INTER_CITY — between two distinct cities (inter-urban delivery)</li>
 *   <li>REMOTE — isolated locations, forest zones, border areas</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public enum DeliveryZoneType {

    /** Urban core zone. Dense population, high POI density, good road coverage. */
    URBAN,

    /** Peri-urban / suburban zone. Growing areas, mixed road quality. */
    PERI_URBAN,

    /**
     * Rural zone. Low population density, limited POI, often dirt/degraded roads.
     * Triggers surcharge in billing DSL.
     */
    RURAL,

    /**
     * Inter-city delivery (origin and destination are in different cities).
     * Triggers inter-city billing mode.
     */
    INTER_CITY,

    /**
     * Remote / isolated zone. Very limited road access, high delivery cost.
     * Requires special vehicle type (moto, 4×4). Maximum billing surcharge.
     */
    REMOTE
}
