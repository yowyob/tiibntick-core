package com.yowyob.tiibntick.bootstrap.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.TimeUnit;

/**
 * Custom reactive health indicator for Kafka, registered under the bean name {@code kafka}
 * so it can be referenced from the {@code readiness} health group in {@code application.yml}.
 * <p>
 * Spring Boot 4 / spring-kafka no longer ship a built-in Kafka health auto-configuration
 * (unlike R2DBC and Redis, which Spring Boot still auto-configures) — this indicator fills
 * that gap by probing cluster connectivity via {@link AdminClient}.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component("kafka")
@RequiredArgsConstructor
public class TntKafkaHealthIndicator implements ReactiveHealthIndicator {

    private final KafkaAdmin kafkaAdmin;

    @Override
    public Mono<Health> health() {
        return Mono.fromCallable(() -> {
                    try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
                        String clusterId = adminClient.describeCluster().clusterId().get(3, TimeUnit.SECONDS);
                        return Health.up()
                                .withDetail("clusterId", clusterId)
                                .build();
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.warn("Kafka health check failed: {}", ex.getMessage());
                    return Mono.just(Health.down()
                            .withDetail("error", ex.getMessage())
                            .build());
                });
    }
}
