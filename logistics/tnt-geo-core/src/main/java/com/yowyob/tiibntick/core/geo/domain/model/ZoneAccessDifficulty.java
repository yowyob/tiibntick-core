package com.yowyob.tiibntick.core.geo.domain.model;

/**
 * Difficulty level for accessing a delivery zone.
 *
 * <p>Used as a DSL variable in the TiiBnTick billing engine
 * ({@code zoneAccessDifficulty}) to apply surcharges for hard-to-reach zones.
 * Computed by {@code IFreelancerOrgGeoUseCase.computeZoneAccessDifficulty()}.
 *
 * <p>Calibration for West African urban/rural context (Cameroon):
 * <ul>
 *   <li>EASY — paved road, flat terrain, no restrictions</li>
 *   <li>MODERATE — light traffic, slight detours, partially degraded road</li>
 *   <li>DIFFICULT — heavy congestion, significant terrain, degraded/dirt road</li>
 *   <li>INACCESSIBLE — flood zone, road closure, no motor vehicle access</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public enum ZoneAccessDifficulty {

    /** Open access, smooth road, no known obstacles. */
    EASY,

    /** Minor obstacles: some traffic, moderate road degradation. */
    MODERATE,

    /**
     * Significant obstacles: heavy traffic, flooded sections, steep terrain,
     * or very degraded laterite track. Requires moto or 4×4.
     */
    DIFFICULT,

    /**
     * Zone currently unreachable by standard delivery vehicles.
     * Example: flooded road, total road closure, conflict zone perimeter.
     */
    INACCESSIBLE
}
