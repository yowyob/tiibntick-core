package com.yowyob.kernel.event.application.port.out;

import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;

import java.util.Map;

/**
 * <b>Outbound port</b> — Publishes a {@link DomainEventEnvelope} to Apache Kafka.
 *
 * <p>Implementations use the Spring Kafka {@code KafkaTemplate} to perform the
 * actual write. The publish is fire-and-forget from the port's perspective; the
 * returned {@link Mono} completes once Kafka acknowledges the write at the
 * configured acknowledgement level (default: {@code acks=all}).
 *
 * <p>Headers injected automatically by the implementation:
 * <ul>
 *   <li>{@code X-Yow-Envelope-Id} — the envelope's unique identifier</li>
 *   <li>{@code X-Yow-Correlation-Id} — the business correlation ID</li>
 *   <li>{@code X-Yow-Solution-Code} — the originating solution (e.g. TNT)</li>
 *   <li>{@code X-Yow-Schema-Version} — the Avro schema version used</li>
 *   <li>{@code X-Yow-Replay} — {@code "true"} for replayed events</li>
 * </ul>
 */
public interface KafkaPublisherPort {

    /**
     * Publishes the payload of the given envelope to its configured Kafka topic.
     *
     * @param envelope     the envelope whose payload is to be published
     * @param extraHeaders additional headers to include in the Kafka record
     *                     (merged with the standard Yowyob headers)
     * @return a {@link Mono} completing when Kafka has acknowledged the write,
     *         or propagating an error on publish failure
     */
    Mono<Void> publish(DomainEventEnvelope envelope, Map<String, String> extraHeaders);

    /**
     * Convenience overload that publishes without extra headers.
     *
     * @param envelope the envelope to publish
     * @return a {@link Mono} completing on acknowledgement
     */
    default Mono<Void> publish(DomainEventEnvelope envelope) {
        return publish(envelope, Map.of());
    }

    /**
     * Publishes the envelope flagged as a replay event ({@code X-Yow-Replay: true}).
     * Idempotent consumers use this header to skip side-effects during replay.
     *
     * @param envelope the original envelope to replay
     * @return a {@link Mono} completing on acknowledgement
     */
    Mono<Void> publishAsReplay(DomainEventEnvelope envelope);
}
