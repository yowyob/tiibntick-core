package com.yowyob.tiibntick.core.incident.port.outbound;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;
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

    /*
     * Structured variants. {@code params} are the facts of the message (reference, level, severity, ...), NOT
     * a sentence: the text is written in the catalog in French and English and rendered for each recipient
     * in their own account language (see tnt-notify-core). {@code title}/{@code body} remain as a plain
     * fallback for adapters that know nothing about the catalog.
     */

    default Mono<Void> notifyActor(UUID actorId, String title, String body, String type, UUID incidentId,
                                   Map<String, Object> params) {
        return notifyActor(actorId, title, body, type, incidentId);
    }

    default Mono<Void> notifyActors(List<UUID> actorIds, String title, String body, String type, UUID incidentId,
                                    Map<String, Object> params) {
        return notifyActors(actorIds, title, body, type, incidentId);
    }

    default Mono<Void> notifyAgency(UUID agencyId, String title, String body, String type, UUID incidentId,
                                    Map<String, Object> params) {
        return notifyAgency(agencyId, title, body, type, incidentId);
    }
}
