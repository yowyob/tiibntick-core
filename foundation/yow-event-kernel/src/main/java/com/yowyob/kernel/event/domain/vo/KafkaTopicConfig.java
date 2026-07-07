package com.yowyob.kernel.event.domain.vo;

/**
 * Descriptor for a Kafka topic used within the Yowyob event bus.
 *
 * <p>Instances are registered at application startup by each Yowyob solution
 * (e.g. TiiBnTick registers {@code tnt.delivery.missions}) and are used by the
 * {@link yowyob.kernel.event.application.port.out.KafkaTopicManagerPort} to
 * ensure the topic exists before any events are published.
 *
 * @param topicName         the fully qualified Kafka topic name
 * @param partitions        number of partitions (defaults to 3 for most topics)
 * @param replicationFactor replication factor (defaults to 1 for development,
 *                          3 in production)
 * @param retentionMs       log retention in milliseconds (-1 means infinite)
 * @param aggregateType     the domain aggregate type this topic carries events for
 * @param solutionCode      the Yowyob solution that owns this topic (e.g. "TNT")
 */
public record KafkaTopicConfig(
        String topicName,
        int partitions,
        short replicationFactor,
        long retentionMs,
        String aggregateType,
        String solutionCode
) {

    // ── Validation ──────────────────────────────────────────────────────────

    public KafkaTopicConfig {
        if (topicName == null || topicName.isBlank()) {
            throw new IllegalArgumentException("topicName must not be blank");
        }
        if (partitions < 1) {
            throw new IllegalArgumentException("partitions must be >= 1");
        }
        if (replicationFactor < 1) {
            throw new IllegalArgumentException("replicationFactor must be >= 1");
        }
        if (solutionCode == null || solutionCode.isBlank()) {
            throw new IllegalArgumentException("solutionCode must not be blank");
        }
    }

    // ── Factory methods ──────────────────────────────────────────────────────

    /**
     * Creates a topic configuration with default settings suitable for most
     * domain event streams.
     *
     * @param topicName     the Kafka topic name
     * @param aggregateType the domain aggregate type
     * @param solutionCode  the owning solution
     * @return a default-configured topic descriptor
     */
    public static KafkaTopicConfig of(
            final String topicName,
            final String aggregateType,
            final String solutionCode) {
        // Default: 3 partitions, RF=1 (dev), 7-day retention
        return new KafkaTopicConfig(topicName, 3, (short) 1, 604_800_000L, aggregateType, solutionCode);
    }

    /**
     * Creates a high-throughput topic configuration for topics that carry
     * high-volume streams (e.g. GPS location events).
     *
     * @param topicName     the Kafka topic name
     * @param aggregateType the domain aggregate type
     * @param solutionCode  the owning solution
     * @return a high-throughput topic descriptor with more partitions
     */
    public static KafkaTopicConfig highThroughput(
            final String topicName,
            final String aggregateType,
            final String solutionCode) {
        return new KafkaTopicConfig(topicName, 12, (short) 1, 86_400_000L, aggregateType, solutionCode);
    }
}
