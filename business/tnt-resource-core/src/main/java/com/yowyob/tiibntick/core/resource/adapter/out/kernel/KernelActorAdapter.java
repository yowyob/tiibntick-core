package com.yowyob.tiibntick.core.resource.adapter.out.kernel;

import com.yowyob.tiibntick.common.kernel.KernelResponses;
import com.yowyob.tiibntick.core.resource.application.port.out.KernelActorPort;
import com.yowyob.tiibntick.core.resource.domain.model.KernelActorDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
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
     * <p>GET /api/actors/{actorId}?tenantId={tenantId}. Note: as of the 2026-07-08 Kernel
     * OpenAPI spec, {@code actor-controller} does not document a single-actor-by-id GET
     * endpoint (only {@code POST /api/actors}, {@code GET/PUT /api/actors/me},
     * {@code PUT /api/actors/{actorId}}) — this call may 404 until the Kernel adds one.
     * The fail-open design below means that gap degrades to "treat as not found" rather
     * than breaking vehicle/equipment assignment. See {@code docs/knowledge/known-issues.md}.</p>
     */
    @Override
    public Mono<KernelActorDto> findActorById(UUID actorId, UUID tenantId) {
        var responseSpec = kernelWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/actors/{id}")
                        .queryParam("tenantId", tenantId)
                        .build(actorId))
                .retrieve();
        return KernelResponses.unwrapObject(responseSpec, KernelActorDto.class, log,
                "findActorById actorId=" + actorId + " tenant=" + tenantId);
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
