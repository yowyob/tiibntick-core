package com.yowyob.tiibntick.core.trust.adapter.out.health;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Non-blocking connectivity probe for {@code yow-trust-event}.
 *
 * <p>Two firing points, both never blocking application startup and never
 * throwing: an immediate probe on {@link ApplicationReadyEvent}, and a
 * self-healing periodic re-probe. Updates {@link TrustAvailabilityGuard}.
 *
 * <p>See §15.2 of {@code TNT_CORE_Connexion_Trust_Module.md} — resilience design.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Component
public class TrustEventConnectivityPoller {

    private final WebClient trustEventHealthClient;
    private final TrustAvailabilityGuard guard;

    public TrustEventConnectivityPoller(
            @Qualifier("trustEventWebClient") final WebClient trustEventHealthClient,
            final TrustAvailabilityGuard guard) {
        this.trustEventHealthClient = trustEventHealthClient;
        this.guard = guard;
    }

    /** Non-blocking startup probe — never throws, never delays application readiness. */
    @EventListener(ApplicationReadyEvent.class)
    public void probeOnStartup() {
        checkConnectivity().subscribe();
    }

    /** Self-healing: re-probes regardless of current state. */
    @Scheduled(fixedDelayString = "${tnt.trust.health-poll-interval-ms:30000}")
    public void probePeriodically() {
        checkConnectivity().subscribe();
    }

    private Mono<Void> checkConnectivity() {
        return trustEventHealthClient.get()
                .uri("/actuator/health")
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(3))
                .doOnSuccess(r -> guard.markAvailable())
                .doOnError(e -> guard.markUnavailable())
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}
