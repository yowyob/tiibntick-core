package com.yowyob.tiibntick.bootstrap.health;

import com.yowyob.tiibntick.bootstrap.bridge.KernelConnectionStatus;
import com.yowyob.tiibntick.bootstrap.bridge.YowyobKernelBridge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Reactive health indicator for the Yowyob Kernel (RT-comops).
 * Exposed at {@code /actuator/health/kernel}.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component("kernel")
@RequiredArgsConstructor
public class TntKernelHealthIndicator implements ReactiveHealthIndicator {

    private final YowyobKernelBridge kernelBridge;

    @Override
    public Mono<Health> health() {
        return kernelBridge.ping()
                .map(connected -> {
                    KernelConnectionStatus status = kernelBridge.getConnectionStatus();
                    if (connected) {
                        Health.Builder builder = Health.up()
                                .withDetail("kernelVersion", status.getKernelVersion())
                                .withDetail("yowAuthReachable", status.isYowAuthReachable())
                                .withDetail("eventBusReachable", status.isEventBusReachable());
                        if (status.getLatencyMs() != null) {
                            builder.withDetail("latencyMs", status.getLatencyMs());
                        }
                        return !status.isFullyOperational()
                                ? Health.status("DEGRADED")
                                    .withDetail("kernelVersion", status.getKernelVersion())
                                    .withDetail("yowAuthReachable", status.isYowAuthReachable())
                                    .withDetail("eventBusReachable", status.isEventBusReachable())
                                    .build()
                                : builder.build();
                    } else {
                        return Health.down()
                                .withDetail("error", status.getErrorMessage())
                                .build();
                    }
                })
                .onErrorResume(ex -> {
                    log.warn("Kernel health check error: {}", ex.getMessage());
                    return Mono.just(Health.down()
                            .withException(ex)
                            .build());
                });
    }
}
