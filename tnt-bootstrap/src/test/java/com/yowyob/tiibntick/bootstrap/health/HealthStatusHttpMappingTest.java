package com.yowyob.tiibntick.bootstrap.health;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.health.actuate.endpoint.SimpleHttpCodeStatusMapper;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot C-21 — the HTTP codes the health endpoints really serve, resolved from the real
 * {@code application.yml} and fed to the same {@link SimpleHttpCodeStatusMapper} Spring Boot
 * builds from {@code management.endpoint.health.status.http-mapping}.
 *
 * <p>A custom http-mapping replaces Spring's defaults rather than extending them. C-20.2 declared
 * only {@code DEGRADED: 200}, which turned DOWN and OUT_OF_SERVICE into HTTP 200 on every health
 * endpoint — liveness and readiness included (measured on a prod-profile container with Redis
 * stopped: readiness DOWN, HTTP 200). The orchestrator's health check could never fail again.
 *
 * <p>Checked for the default document (what production actually runs: its compose file does not
 * activate the {@code prod} profile) and for the {@code prod} profile.
 */
class HealthStatusHttpMappingTest {

    @Configuration
    static class EmptyConfig {
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "prod"})
    void downAndOutOfServiceStay503_degradedIs200(String profile) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(EmptyConfig.class)
                .web(WebApplicationType.NONE);
        if (!"default".equals(profile)) {
            builder.profiles(profile);
        }
        try (ConfigurableApplicationContext context = builder.run()) {
            Map<String, Integer> mapping = Binder.get(context.getEnvironment())
                    .bind("management.endpoint.health.status.http-mapping",
                            Bindable.mapOf(String.class, Integer.class))
                    .orElse(null);
            SimpleHttpCodeStatusMapper mapper = new SimpleHttpCodeStatusMapper(mapping);

            assertThat(mapper.getStatusCode(Status.DOWN)).as("DOWN").isEqualTo(503);
            assertThat(mapper.getStatusCode(Status.OUT_OF_SERVICE)).as("OUT_OF_SERVICE").isEqualTo(503);
            assertThat(mapper.getStatusCode(new Status("DEGRADED"))).as("DEGRADED").isEqualTo(200);
            assertThat(mapper.getStatusCode(Status.UP)).as("UP").isEqualTo(200);
        }
    }
}
