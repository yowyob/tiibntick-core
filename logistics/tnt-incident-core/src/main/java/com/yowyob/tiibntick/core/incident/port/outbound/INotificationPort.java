package com.yowyob.tiibntick.core.incident.port.outbound;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.UUID;
/**
 * Outbound port: send push notifications and SMS alerts via tnt-notify-core.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface INotificationPort {
    Mono<Void> notifyActor(UUID actorId, String title, String body, String type, UUID incidentId);
    Mono<Void> notifyActors(List<UUID> actorIds, String title, String body, String type, UUID incidentId);
    Mono<Void> notifyAgency(UUID agencyId, String title, String body, String type, UUID incidentId);
}
