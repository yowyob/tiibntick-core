package com.yowyob.tiibntick.core.trust.adapter.out.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central runtime flag tracking {@code yow-trust-event} availability.
 *
 * <p>Read by {@code LogisticEventPublisherService} before every publish attempt
 * (fast path: skip the Kafka/circuit-breaker call entirely and enqueue for retry
 * when known unavailable, avoiding needless circuit-breaker trips). Updated by
 * the startup probe and scheduled poller ({@link TrustEventConnectivityPoller}).
 *
 * <p>See §15.2 of {@code TNT_CORE_Connexion_Trust_Module.md} — resilience design.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Component
public class TrustAvailabilityGuard {

    private static final Logger log = LoggerFactory.getLogger(TrustAvailabilityGuard.class);

    private final AtomicBoolean available = new AtomicBoolean(false);

    public boolean isAvailable() {
        return available.get();
    }

    public void markAvailable() {
        if (available.compareAndSet(false, true)) {
            log.info("tnt-trust-core: yow-trust-event reachable — publication reactivated");
        }
    }

    public void markUnavailable() {
        if (available.compareAndSet(true, false)) {
            log.warn("tnt-trust-core: yow-trust-event unreachable — degrading to retry-queue mode");
        }
    }
}
