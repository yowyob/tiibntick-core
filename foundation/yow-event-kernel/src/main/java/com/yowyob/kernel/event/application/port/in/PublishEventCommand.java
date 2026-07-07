package com.yowyob.kernel.event.application.port.in;

import com.yowyob.kernel.event.domain.vo.RetryPolicy;

/**
 * Command record carrying all data required to publish a single domain event
 * to the Yowyob transactional outbox.
 *
 * <p>Records are the preferred command type in the Yowyob Kernel port layer
 * (aligned with the {@code RT-comops} convention). They are immutable,
 * serialisation-friendly and expressly document the required inputs.
 *
 * @param correlationId    business correlation identifier propagated across services
 * @param causationId      optional: ID of the command/event that caused this event
 * @param eventType        fully qualified event type name (e.g. {@code MissionCreatedEvent})
 * @param aggregateId      identifier of the aggregate that emitted this event
 * @param aggregateType    aggregate type discriminator (e.g. {@code Mission})
 * @param tenantId         multi-tenant isolation key
 * @param solutionCode     Yowyob solution code (e.g. {@code TNT})
 * @param payload          JSON-serialised event payload
 * @param schemaVersion    Avro schema version used (defaults to 1 if omitted)
 * @param kafkaTopic       target Kafka topic name
 * @param kafkaPartitionKey Kafka partition key (defaults to {@code aggregateId} if blank)
 * @param retryPolicy      custom retry policy; {@code null} applies the default outbox policy
 */
public record PublishEventCommand(
        String correlationId,
        String causationId,
        String eventType,
        String aggregateId,
        String aggregateType,
        String tenantId,
        String solutionCode,
        String payload,
        int    schemaVersion,
        String kafkaTopic,
        String kafkaPartitionKey,
        RetryPolicy retryPolicy
) {

    /**
     * Minimal factory — creates a command with only the required fields.
     * Schema version defaults to {@code 1}, retry policy uses the default outbox policy.
     */
    public static PublishEventCommand of(
            final String correlationId,
            final String eventType,
            final String aggregateId,
            final String aggregateType,
            final String tenantId,
            final String solutionCode,
            final String payload,
            final String kafkaTopic) {
        return new PublishEventCommand(
            correlationId, null, eventType, aggregateId, aggregateType,
            tenantId, solutionCode, payload, 1, kafkaTopic,
            aggregateId,   // default partition key = aggregateId
            null           // use default retry policy
        );
    }
}
