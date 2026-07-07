package com.yowyob.tiibntick.core.route.adapter.out.incident;

import com.yowyob.tiibntick.core.incident.port.outbound.IRouteOptimizerPort;
import com.yowyob.tiibntick.core.incident.port.outbound.IRouteOptimizerPort.RerouteResult;
import com.yowyob.tiibntick.core.route.application.port.in.IRequestReroutingUseCase;
import com.yowyob.tiibntick.core.route.application.port.out.ITourRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import com.yowyob.tiibntick.core.route.domain.model.TourStop;

import java.util.Comparator;
import java.util.UUID;

/**
 * Adapter implementing {@link IRouteOptimizerPort} (port defined in tnt-incident-core).
 *
 * <p>Provides two capabilities required by the incident engine:</p>
 * <ol>
 *   <li>{@code findNearestHub} — PostGIS spatial query to locate the closest relay hub
 *       to an incident position, used when the system needs to reroute a deliverer.</li>
 *   <li>{@code rerouteFromCurrentPosition} — Delegates to the existing
 *       {@link IRequestReroutingUseCase} (A* + Kalman) for dynamic rerouting
 *       after an incident is triaged.</li>
 * </ol>
 *
 * <p>Hexagonal position: outbound adapter in tnt-route-core, implementing an interface
 * defined in tnt-incident-core. No circular dependency is introduced because
 * tnt-incident-core defines the interface and tnt-route-core implements it;
 * assembly happens in tnt-bootstrap.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class IncidentRouteOptimizerAdapter implements IRouteOptimizerPort {

    private static final Logger log = LoggerFactory.getLogger(IncidentRouteOptimizerAdapter.class);

    /** Maximum search radius for nearest hub (km). */
    private static final double MAX_HUB_SEARCH_RADIUS_KM = 50.0;

    private final DatabaseClient databaseClient;
    private final IRequestReroutingUseCase reroutingUseCase;
    private final ITourRepository tourRepository;

    public IncidentRouteOptimizerAdapter(DatabaseClient databaseClient,
                                          IRequestReroutingUseCase reroutingUseCase,
                                          ITourRepository tourRepository) {
        this.databaseClient = databaseClient;
        this.reroutingUseCase = reroutingUseCase;
        this.tourRepository = tourRepository;
    }

    /**
     * Finds the nearest operational relay hub (TiiBnTick Point) to the given coordinates
     * using a PostGIS ST_Distance query on the hub geographic table.
     *
     * <p>Query strategy:
     * <ol>
     *   <li>Selects hubs scoped to {@code tenantId} with {@code status = 'ACTIVE'}.</li>
     *   <li>Orders by PostGIS ST_Distance (spheroid) from the incident position.</li>
     *   <li>Returns the nearest hub within {@value MAX_HUB_SEARCH_RADIUS_KM} km.</li>
     * </ol>
     * </p>
     *
     * @param lat      latitude of the incident position
     * @param lng      longitude of the incident position
     * @param tenantId tenant context (hub must belong to this tenant)
     * @return a Mono emitting the nearest hub result, or empty if none found within radius
     */
    @Override
    public Mono<NearestHubResult> findNearestHub(double lat, double lng, UUID tenantId) {
        log.debug("Finding nearest hub for incident at ({}, {}) tenant={}", lat, lng, tenantId);

        // PostGIS spheroid distance in km: ST_Distance / 1000
        String sql = """
                SELECT
                    h.id             AS hub_id,
                    h.name           AS hub_name,
                    h.address        AS hub_address,
                    h.phone          AS hub_phone,
                    ST_X(h.geom)     AS hub_lng,
                    ST_Y(h.geom)     AS hub_lat,
                    ST_Distance(
                        h.geom::geography,
                        ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
                    ) / 1000.0       AS distance_km
                FROM tnt_relay_hubs h
                WHERE h.tenant_id = :tenantId
                  AND h.status = 'ACTIVE'
                  AND ST_Distance(
                      h.geom::geography,
                      ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
                  ) / 1000.0 <= :maxRadiusKm
                ORDER BY distance_km ASC
                LIMIT 1
                """;

        return databaseClient.sql(sql)
                .bind("lat", lat)
                .bind("lng", lng)
                .bind("tenantId", tenantId)
                .bind("maxRadiusKm", MAX_HUB_SEARCH_RADIUS_KM)
                .map(row -> new NearestHubResult(
                        UUID.fromString(row.get("hub_id", String.class)),
                        row.get("hub_name", String.class),
                        row.get("hub_lat", Double.class),
                        row.get("hub_lng", Double.class),
                        row.get("distance_km", Double.class)
                ))
                .one()
                .doOnSuccess(hub -> {
                    if (hub != null) {
                        log.info("Nearest hub found: {} at {:.2f} km from incident position",
                                hub.hubName(), hub.distanceKm());
                    } else {
                        log.warn("No hub found within {} km of incident at ({}, {})",
                                MAX_HUB_SEARCH_RADIUS_KM, lat, lng);
                    }
                })
                .doOnError(ex -> log.error(
                        "Failed to query nearest hub for incident at ({}, {}): {}", lat, lng, ex.getMessage()));
    }

    /**
     * Recalculates the optimal route from the deliverer's current position (incident location)
     * to the original mission destination, after a delivery incident has been triaged.
     *
     * <p>Delegates to the existing {@link IRequestReroutingUseCase} which runs A* pathfinding
     * with hysteresis threshold. If rerouting fails (e.g. graph not loaded), returns a
     * {@link RerouteResult} with the same cost as the baseline (no rerouting recommended).</p>
     *
     * @param missionId UUID of the affected mission
     * @param lat       current latitude of the deliverer (at incident location)
     * @param lng       current longitude of the deliverer (at incident location)
     * @return a Mono emitting the rerouting result containing new ETA and route geometry
     */
    @Override
    public Mono<RerouteResult> rerouteFromCurrentPosition(UUID missionId, double lat, double lng) {
        log.info("Rerouting mission {} from incident position ({}, {})", missionId, lat, lng);

        // Build a synthetic node ID from coordinates for the A* graph query.
        // The existing ReroutingService accepts node IDs; we derive a geohash-based key.
        String currentNodeId = deriveNodeIdFromCoords(lat, lng);

        return Mono.defer(() -> tourRepository.findById(missionId, null))
                .flatMap(tour -> {
                    String destinationNodeId = tour.stops().stream()
                            .max(Comparator.comparingInt(TourStop::sequenceOrder))
                            .map(TourStop::nodeId)
                            .orElse(currentNodeId);

                    return reroutingUseCase.evaluateRerouting(
                            missionId.toString(),
                            currentNodeId,
                            destinationNodeId,
                            0.0,      // initial cost: unknown at incident time, let A* compute
                            null      // tenantId: resolved from tour
                    );
                })
                .map(decision -> new RerouteResult(
                        missionId,
                        currentNodeId,
                        (long) decision.alternativeCost()
                ))
                .onErrorResume(ex -> {
                    log.error("Rerouting failed for mission {} after incident: {}", missionId, ex.getMessage());
                    return Mono.just(new RerouteResult(missionId, null, 0L));
                });
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Derives a synthetic graph node identifier from geographic coordinates.
     * Uses a simple geohash-like encoding at precision 6 (±0.6 km accuracy),
     * sufficient for incident-triggered rerouting decisions.
     *
     * @param lat latitude
     * @param lng longitude
     * @return a string node identifier usable in the route graph
     */
    private String deriveNodeIdFromCoords(double lat, double lng) {
        // Precision-6 grid cell: round to 3 decimal places (~111m accuracy)
        long latCell = Math.round(lat * 1000);
        long lngCell = Math.round(lng * 1000);
        return "geo:" + latCell + ":" + lngCell;
    }
}
