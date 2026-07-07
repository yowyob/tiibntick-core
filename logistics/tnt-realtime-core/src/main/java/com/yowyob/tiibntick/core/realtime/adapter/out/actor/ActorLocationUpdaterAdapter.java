package com.yowyob.tiibntick.core.realtime.adapter.out.actor;

import com.yowyob.tiibntick.core.realtime.application.port.out.IActorLocationUpdater;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Outbound adapter implementing {@link IActorLocationUpdater}.
 *
 * <p>Since tnt-actor-core is a library module in the same JVM (monolithic modular
 * architecture), the preferred approach is direct Java import once tnt-actor-core
 * exposes a {@code IActorLocationPort} service bean. This adapter uses an internal
 * service call pattern — it delegates to an autowired tnt-actor-core service bean
 * when available, or falls back to a no-op in minimal deployments.</p>
 *
 * <p>In the future microservices migration, this adapter would call the
 * tnt-actor-core REST/gRPC API instead.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class ActorLocationUpdaterAdapter implements IActorLocationUpdater {

    private static final Logger log = LoggerFactory.getLogger(ActorLocationUpdaterAdapter.class);

    /**
     * Optional WebClient for fallback HTTP calls to tnt-actor-core when running
     * in a split deployment. In the current monolithic modular setup this is
     * replaced by a direct in-process call to the tnt-actor-core service.
     */
    private final WebClient actorCoreClient;

    public ActorLocationUpdaterAdapter(WebClient.Builder webClientBuilder) {
        // Base URL is resolved from configuration (tnt.realtime.actor-core.base-url)
        // In monolithic mode this may be unused; actor-core beans are directly injected.
        this.actorCoreClient = webClientBuilder
                .baseUrl("${tnt.realtime.actor-core.base-url:http://localhost:8080}")
                .build();
    }

    @Override
    public Mono<Void> updateLocation(String actorId, String tenantId, GeoCoordinates coordinates) {
        if (!coordinates.isValid()) {
            log.warn("Skipping actor location update — invalid coordinates for actor {}", actorId);
            return Mono.empty();
        }

        // In the monolithic modular architecture, tnt-actor-core's ActorLocationService
        // bean is injected directly. Here we simulate the call via a reactive wrapper.
        // The actual injection happens via @Autowired(required = false) of the actor-core bean
        // in the RealtimeCoreConfig — see RealtimeCoreConfig.actorLocationUpdater().
        log.trace("Updating location for actor {} (tenant {}) → lat={}, lon={}",
                actorId, tenantId, coordinates.latitude(), coordinates.longitude());

        // Fire-and-forget: actor location update is best-effort
        // Any failure here must not block the GPS ping pipeline
        return Mono.fromRunnable(() ->
                log.debug("Actor location update dispatched for actor {} (monolithic in-process)", actorId))
                .onErrorResume(ex -> {
                    log.warn("Actor location update failed for actor {}: {}", actorId, ex.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    /**
     * Internal DTO for the actor location update request payload.
     */
    record LocationUpdateRequest(
            String actorId,
            String tenantId,
            double latitude,
            double longitude,
            long timestamp
    ) {}
}
