package com.yowyob.tiibntick.core.trust.adapter.out.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Reactive health indicator surfacing {@link TrustAvailabilityGuard}'s state.
 * Exposed at {@code /actuator/health/trustEventGateway}.
 *
 * <p>An unavailable {@code yow-trust-event} link degrades to {@code DEGRADED},
 * never {@code DOWN} — it must never take {@code tnt-bootstrap} out of the
 * Kubernetes readiness rotation. See §15.6 of
 * {@code TNT_CORE_Connexion_Trust_Module.md} — the {@code readiness} health
 * group in {@code application.yml} deliberately does not include this
 * indicator's bean name.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Component("trustEventGateway")
public class TrustEventHealthIndicator implements ReactiveHealthIndicator {

    private final TrustAvailabilityGuard guard;

    public TrustEventHealthIndicator(final TrustAvailabilityGuard guard) {
        this.guard = guard;
    }

    @Override
    public Mono<Health> health() {
        return Mono.just(guard.isAvailable()
                ? Health.up().build()
                : Health.status("DEGRADED").withDetail("reason", "yow-trust-event unreachable").build());
    }
}
