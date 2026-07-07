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
     * <p> — Added to support cross-module event communication with tnt-organization-core.
     *
     * @param topic   the Kafka topic to publish to
     * @param payload the event payload map
     * @return Mono completing when the event is sent
     */
    default Mono<Void> publish(String topic, Map<String, Object> payload) {
        // Default no-op — override in production adapter
        return Mono.empty();
    }
}
