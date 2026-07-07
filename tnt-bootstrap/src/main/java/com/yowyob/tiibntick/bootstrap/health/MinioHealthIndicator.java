package com.yowyob.tiibntick.bootstrap.health;

import io.minio.MinioClient;
import io.minio.ListBucketsArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Custom reactive health indicator for MinIO object storage.
 * <p>
 * Spring Boot Actuator already provides health indicators for PostgreSQL (R2DBC),
 * Redis and Kafka. This bean adds MinIO to the composite health check at
 * {@code /actuator/health/minio}.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component("minio")
@RequiredArgsConstructor
public class MinioHealthIndicator implements ReactiveHealthIndicator {

    private final MinioClient minioClient;

    @Override
    public Mono<Health> health() {
        return Mono.fromCallable(() -> {
                    minioClient.listBuckets(ListBucketsArgs.builder().build());
                    return Health.up()
                            .withDetail("storage", "MinIO")
                            .withDetail("status", "reachable")
                            .build();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.warn("MinIO health check failed: {}", ex.getMessage());
                    return Mono.just(Health.down()
                            .withDetail("storage", "MinIO")
                            .withDetail("error", ex.getMessage())
                            .build());
                });
    }
}
