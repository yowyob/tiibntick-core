package com.yowyob.tiibntick.core.administration.application.port.out;

import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Outbound port for publishing TiiBnTick administration events to Kafka.
 *
 * <p> — Added topic-targeted publish overload for FreelancerOrg admin lifecycle events.
 *
 * @author MANFOUO Braun
 */
public interface TntAdministrationEventPublisher {

    /**
     * Publishes a structured event to a specific Kafka topic.
     * Used by domain administration events (role provisioning, options updates, etc.)
     */
    Mono<Void> publish(UUID tenantId, String eventType, String module, UUID aggregateId, Map<String, Object> payload);

    /**
     * Publishes a simple payload map to a named Kafka topic.
     * Used by FreelancerOrg admin lifecycle events (KYC approval, suspension, etc.)
     *
     * <p>Used to have a default no-op body — {@code FreelancerOrgAdminService} called this
     * overload exclusively (never the 5-arg one above), so every FreelancerOrg KYC/suspension/
     * blacklist event was silently swallowed. Made abstract so a missing implementation now
     * fails to compile instead of failing silently at runtime.
     *
     * @param topic    the Kafka topic to publish to
     * @param tenantId the tenant the event belongs to (required by the outbox envelope)
     * @param payload  the event payload map
     * @return Mono completing when the event is sent
     */
    Mono<Void> publish(String topic, UUID tenantId, Map<String, Object> payload);
}
