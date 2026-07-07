package com.yowyob.tiibntick.bootstrap.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer metrics configuration for TiiBnTick Core.
 * <p>
 * Registers custom application-level counters and timers exposed via
 * {@code /actuator/prometheus} and collected by Prometheus.
 * <p>
 * v2.1 changes: Added {@code tntDisputesOpenedCounter} and {@code tntDisputeResolutionTimer}
 * for the tnt-dispute-core module.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Configuration
public class TntMetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> tntMeterRegistryCustomizer() {
        return registry -> registry.config()
                .commonTags("application", "tiibntick-core", "solution", "TNT");
    }

    // ── Delivery & Route metrics ────────────────────────────────────────────

    @Bean
    public Counter tntMissionsCounter(MeterRegistry registry) {
        return Counter.builder("tnt.missions.total")
                .description("Total number of delivery missions created")
                .tag("module", "tnt-delivery-core")
                .register(registry);
    }

    @Bean
    public Timer tntRouteOptDurationTimer(MeterRegistry registry) {
        return Timer.builder("tnt.route.optimization.duration")
                .description("Time taken by the VRP/A* route optimization (OR-Tools)")
                .tag("module", "tnt-route-core")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .register(registry);
    }

    @Bean
    public Timer tntKalmanUpdateTimer(MeterRegistry registry) {
        return Timer.builder("tnt.kalman.eta.update.duration")
                .description("Time taken to compute Kalman filter ETA update")
                .tag("module", "tnt-route-core")
                .register(registry);
    }

    // ── Billing metrics ────────────────────────────────────────────────────

    @Bean
    public Timer tntBillingEvaluationTimer(MeterRegistry registry) {
        return Timer.builder("tnt.billing.evaluation.duration")
                .description("Time taken to evaluate a billing DSL expression")
                .tag("module", "tnt-billing-dsl")
                .publishPercentiles(0.5, 0.95)
                .register(registry);
    }

    // ── Kernel interaction metrics ─────────────────────────────────────────

    @Bean
    public Counter tntKernelCallsCounter(MeterRegistry registry) {
        return Counter.builder("tnt.kernel.calls.total")
                .description("Total number of calls made to the Yowyob Kernel bridge")
                .tag("module", "tnt-bootstrap")
                .register(registry);
    }

    // ── Dispute metrics (v2.1 — tnt-dispute-core) ──────────────────────────

    @Bean
    public Counter tntDisputesOpenedCounter(MeterRegistry registry) {
        return Counter.builder("tnt.disputes.opened.total")
                .description("Total number of delivery disputes opened")
                .tag("module", "tnt-dispute-core")
                .register(registry);
    }

    @Bean
    public Timer tntDisputeResolutionTimer(MeterRegistry registry) {
        return Timer.builder("tnt.disputes.resolution.duration")
                .description("Median duration to resolve a delivery dispute")
                .tag("module", "tnt-dispute-core")
                .publishPercentiles(0.5, 0.75, 0.95)
                .register(registry);
    }
}
