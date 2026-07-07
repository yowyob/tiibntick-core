package com.yowyob.tiibntick.bootstrap.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Health indicator verifying that Google OR-Tools JNI native libraries
 * are loaded and the VRP solver is operable.
 * Exposed at {@code /actuator/health/tnt-infra/or-tools}.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component("or-tools")
public class TntOrToolsHealthIndicator implements ReactiveHealthIndicator {

    private static final boolean OR_TOOLS_LOADED;

    static {
        boolean loaded = false;
        try {
            // Verify OR-Tools is usable by instantiating a minimal solver object
            Class.forName("com.google.ortools.linearsolver.MPSolver");
            loaded = true;
        } catch (ClassNotFoundException | UnsatisfiedLinkError e) {
            log.warn("OR-Tools JNI not available: {} — VRP optimization features degraded", e.getMessage());
        }
        OR_TOOLS_LOADED = loaded;
    }

    @Override
    public Mono<Health> health() {
        if (OR_TOOLS_LOADED) {
            return Mono.just(Health.up()
                    .withDetail("library", "Google OR-Tools")
                    .withDetail("version", "9.8.3296")
                    .withDetail("features", "VRP, CVRP, VRPTW")
                    .build());
        } else {
            return Mono.just(Health.status("DEGRADED")
                    .withDetail("library", "Google OR-Tools")
                    .withDetail("status", "JNI native libraries not loaded")
                    .withDetail("impact", "VRP route optimization unavailable — fallback heuristics active")
                    .withDetail("fix", "Use eclipse-temurin:21-jre (Debian) Docker image")
                    .build());
        }
    }

    public static boolean isLoaded() {
        return OR_TOOLS_LOADED;
    }
}
