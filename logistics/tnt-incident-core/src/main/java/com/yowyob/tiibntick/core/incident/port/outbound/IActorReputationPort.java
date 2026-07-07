package com.yowyob.tiibntick.core.incident.port.outbound;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * Outbound port: update actor reputation scores and flag fraud via tnt-actor-core.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IActorReputationPort {
    Mono<Void> decreaseReputation(UUID actorId, double points, String reason);
    Mono<Void> flagForFraud(UUID actorId, UUID incidentId, String evidence);
    Mono<Double> getReputationScore(UUID actorId);
    Mono<Integer> getIncidentHistoryCount(UUID actorId);
}
