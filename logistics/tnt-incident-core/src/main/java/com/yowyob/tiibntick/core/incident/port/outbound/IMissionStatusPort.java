package com.yowyob.tiibntick.core.incident.port.outbound;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.UUID;
/**
 * Outbound port: pause and resume delivery missions via tnt-delivery-core.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IMissionStatusPort {
    Mono<Void> pauseMission(UUID missionId, UUID incidentId);
    Mono<Void> resumeMission(UUID missionId, UUID newDriverId, UUID newVehicleId);
    Mono<MissionSnapshot> getMissionSnapshot(UUID missionId);
    record MissionSnapshot(UUID missionId, UUID currentDriverId, UUID currentVehicleId,
                           UUID agencyId, String status, Instant slaDeadline,
                           java.util.List<UUID> parcelIds) {}
}
