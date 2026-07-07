package com.yowyob.kernel.event.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.domain.vo.KafkaTopicConfig;

/**
 * <b>Outbound port</b> — Manages Kafka topic lifecycle (create / describe / delete).
 *
 * <p>Topics are registered at startup by each Yowyob solution and created if
 * they do not already exist in the Kafka cluster. This port abstracts the
 * Kafka AdminClient so that the application layer remains decoupled from the
 * Kafka client library.
 */
public interface KafkaTopicManagerPort {

    /**
     * Ensures the topic described by the configuration exists in the Kafka cluster.
     * If the topic already exists and its configuration matches, the operation is
     * a no-op.
     *
     * @param config the topic descriptor
     * @return a {@link Mono} completing empty when the topic is ready
     */
    Mono<Void> ensureExists(KafkaTopicConfig config);

    /**
     * Creates or updates multiple topics in a single admin request.
     *
     * @param configs the topic descriptors to apply
     * @return a {@link Mono} emitting the count of topics created or updated
     */
    Mono<Integer> ensureAllExist(Iterable<KafkaTopicConfig> configs);

    /**
     * Verifies whether the named topic exists in the Kafka cluster.
     *
     * @param topicName the topic name to check
     * @return a {@link Mono} emitting {@code true} if the topic exists
     */
    Mono<Boolean> topicExists(String topicName);

    /**
     * Lists all topic names currently registered in the cluster.
     *
     * @return a {@link Flux} of topic names
     */
    Flux<String> listTopics();
}
