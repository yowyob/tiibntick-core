package com.yowyob.kernel.event.integration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;

/**
 * Spring Boot test configuration that activates the {@code yow-event-kernel}
 * autoconfiguration in a minimal test application context.
 *
 * <p>A {@link SimpleMeterRegistry} is registered so that {@code MicrometerEventMetrics}
 * can be wired without requiring a full Prometheus or Grafana setup.
 *
 * <p>Used exclusively in {@link YowEventKernelIntegrationTest}. The infrastructure
 * (PostgreSQL, Kafka, Redis) is provided by Testcontainers via
 * {@link org.springframework.test.context.DynamicPropertySource}.
 */
@Configuration
@EnableAutoConfiguration
@Import(YowEventKernelAutoConfiguration.class)
public class YowEventKernelTestConfig {

    /**
     * Registers a simple in-memory {@link MeterRegistry} for use in tests.
     * Replaces the Prometheus registry that would normally be configured in production.
     */
    @Bean
    public MeterRegistry testMeterRegistry() {
        return new SimpleMeterRegistry();
    }
}
