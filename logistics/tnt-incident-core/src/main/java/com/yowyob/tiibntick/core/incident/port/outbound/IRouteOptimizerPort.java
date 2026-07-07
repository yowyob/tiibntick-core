package com.yowyob.tiibntick.core.incident.port.outbound;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * Outbound port: request route recalculation and nearest hub lookup from tnt-route-core.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IRouteOptimizerPort {
    Mono<NearestHubResult> findNearestHub(double lat, double lng, UUID tenantId);
    Mono<RerouteResult> rerouteFromCurrentPosition(UUID missionId, double lat, double lng);
    record NearestHubResult(UUID hubId, String hubName, double lat, double lng, double distanceKm) {}
    record RerouteResult(UUID missionId, String newRoutePolyline, long newEtaMinutes) {}
}
