package com.yowyob.kernel.event.adapter.kafka;

import com.yowyob.kernel.event.application.port.out.BrokerAvailabilityPort;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * {@link BrokerAvailabilityPort} backed by the Kafka {@link AdminClient}: a {@code describeCluster}
 * call bounded by {@link #PROBE_TIMEOUT}.
 */
@Component
public class KafkaBrokerAvailabilityAdapter implements BrokerAvailabilityPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaBrokerAvailabilityAdapter.class);

    static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);

    private final AdminClient adminClient;

    public KafkaBrokerAvailabilityAdapter(final AdminClient adminClient) {
        this.adminClient = Objects.requireNonNull(adminClient);
    }

    @Override
    public Mono<Boolean> isAvailable() {
        return Mono.fromCallable(() -> {
                int timeoutMs = (int) PROBE_TIMEOUT.toMillis();
                adminClient.describeCluster(new DescribeClusterOptions().timeoutMs(timeoutMs))
                    .clusterId().get(timeoutMs + 500L, TimeUnit.MILLISECONDS);
                return true;
            })
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(e -> {
                log.debug("Kafka broker unreachable: {}", e.toString());
                return Mono.just(false);
            });
    }
}
