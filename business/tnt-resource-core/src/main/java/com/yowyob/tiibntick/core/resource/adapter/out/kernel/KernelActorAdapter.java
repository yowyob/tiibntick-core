package com.yowyob.tiibntick.core.resource.adapter.out.kernel;

import com.yowyob.tiibntick.core.resource.application.port.out.KernelActorPort;
import com.yowyob.tiibntick.core.resource.domain.model.KernelActorDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter — Kernel Actor Bridge via reactive HTTP (WebClient).
 *
 * <p>Implements {@link KernelActorPort} by calling the Yowyob Kernel REST API
 * (RT-comops-actor-core). All calls are non-blocking (Reactor Mono).</p>
 *
 * <p>Design contract (resilient by design):
 * <ul>
 *   <li>HTTP 404 → {@code Mono.empty()} — actor not found, caller decides action.</li>
 *   <li>Network or timeout errors → {@code Mono.empty()} + WARN log — vehicle
 *       assignment is never hard-blocked by Kernel unavailability.</li>
 *   <li>For {@code isActiveActor}: any error returns {@code false} without throwing.</li>
 * </ul>
 * </p>
 *
 * <p>WebClient bean provided by
 * {@link com.yowyob.tiibntick.common.config.KernelWebClientConfig}.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class KernelActorAdapter implements KernelActorPort {

    private static final Logger log = LoggerFactory.getLogger(KernelActorAdapter.class);

    private final WebClient kernelWebClient;

    public KernelActorAdapter(@Qualifier("kernelWebClient") WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    /**
     * {@inheritDoc}
     *
     * <p>GET /actors/{actorId}?tenantId={tenantId}</p>
     */
    @Override
    public Mono<KernelActorDto> findActorById(UUID actorId, UUID tenantId) {
        return kernelWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/actors/{id}")
                        .queryParam("tenantId", tenantId)
                        .build(actorId))
                .retrieve()
                .bodyToMono(KernelActorDto.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    log.debug("Kernel actor not found: actorId={} tenant={}", actorId, tenantId);
                    return Mono.empty();
                })
                .onErrorResume(Exception.class, ex -> {
                    log.warn("Kernel actor bridge error for actorId={}: {}", actorId, ex.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>HEAD /actors/{actorId}?tenantId={tenantId} — returns true if active, false on any error.</p>
     */
    @Override
    public Mono<Boolean> isActiveActor(UUID actorId, UUID tenantId) {
        return findActorById(actorId, tenantId)
                .map(KernelActorDto::isActive)
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }
}
