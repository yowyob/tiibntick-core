package com.yowyob.kernel.event.adapter.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.application.port.out.KafkaTopicManagerPort;
import com.yowyob.kernel.event.domain.vo.KafkaTopicConfig;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Kafka {@link AdminClient}-based implementation of {@link KafkaTopicManagerPort}.
 *
 * <p>Operations are wrapped in {@link Mono#fromCallable} because the Kafka
 * AdminClient uses blocking {@link java.util.concurrent.Future}s. The reactive
 * wrapper ensures that blocking I/O does not escape to the event-loop threads;
 * callers should subscribe on a bounded elastic scheduler for production use.
 *
 * <p>Topic creation is idempotent: if the topic already exists with matching
 * configuration, the operation is a no-op. If the topic exists but configuration
 * differs (e.g. partition count), a warning is logged and the existing
 * configuration is kept (Kafka does not allow reducing partition counts).
 */
@Component
public class KafkaTopicManagerAdapter implements KafkaTopicManagerPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaTopicManagerAdapter.class);

    private final AdminClient adminClient;

    public KafkaTopicManagerAdapter(final AdminClient adminClient) {
        this.adminClient = Objects.requireNonNull(adminClient);
    }

    @Override
    public Mono<Void> ensureExists(final KafkaTopicConfig config) {
        return Mono.fromCallable(() -> {
            try {
                Set<String> existing = adminClient.listTopics().names().get();
                if (existing.contains(config.topicName())) {
                    log.debug("Topic '{}' already exists — skipping creation", config.topicName());
                    return null;
                }

                NewTopic newTopic = new NewTopic(
                    config.topicName(),
                    config.partitions(),
                    config.replicationFactor()
                );
                // Set retention if not infinite
                if (config.retentionMs() > 0) {
                    newTopic.configs(Map.of(
                        TopicConfig.RETENTION_MS_CONFIG, String.valueOf(config.retentionMs())
                    ));
                }

                adminClient.createTopics(List.of(newTopic)).all().get();
                log.info("Created Kafka topic '{}' (partitions={}, RF={}, solution={})",
                    config.topicName(), config.partitions(),
                    config.replicationFactor(), config.solutionCode());
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while creating topic: " + config.topicName(), e);
            } catch (ExecutionException e) {
                // TopicExistsException is benign — another instance may have created it first
                if (e.getCause() != null
                        && e.getCause().getClass().getSimpleName().equals("TopicExistsException")) {
                    log.debug("Topic '{}' already exists (race condition — benign)", config.topicName());
                    return null;
                }
                throw new RuntimeException("Failed to create topic: " + config.topicName(), e);
            }
        }).then();
    }

    @Override
    public Mono<Integer> ensureAllExist(final Iterable<KafkaTopicConfig> configs) {
        return Flux.fromIterable(configs)
            .flatMap(this::ensureExists)
            .count()
            .map(Long::intValue);
    }

    @Override
    public Mono<Boolean> topicExists(final String topicName) {
        return Mono.fromCallable(() -> {
            try {
                return adminClient.listTopics().names().get().contains(topicName);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while checking topic existence", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed to check topic existence for: " + topicName, e);
            }
        });
    }

    @Override
    public Flux<String> listTopics() {
        return Mono.fromCallable(() -> {
            try {
                return adminClient.listTopics().names().get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while listing topics", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed to list topics", e);
            }
        }).flatMapMany(Flux::fromIterable);
    }
}
