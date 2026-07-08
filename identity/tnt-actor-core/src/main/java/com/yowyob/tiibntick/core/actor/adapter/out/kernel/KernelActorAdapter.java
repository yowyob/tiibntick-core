package com.yowyob.tiibntick.core.actor.adapter.out.kernel;

import com.yowyob.tiibntick.common.kernel.KernelResponses;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelActorPort;
import com.yowyob.tiibntick.core.actor.domain.model.KernelActorDto;
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
 * <p>Implements {@link IKernelActorPort} by calling the Yowyob Kernel's
 * {@code actor-controller} REST API. All calls are non-blocking (Reactor Mono),
 * unwrap the Kernel's {@code {success,data,...}} envelope via
 * {@link KernelResponses} (see {@code docs/architecture/decisions.md} ADR-012),
 * and are fail-open: 404 or any error resolves to empty/false rather than
 * propagating — a Kernel-unreachable window must never block actor-profile
 * creation.</p>
 *
 * <p><b>Known gap</b>: as of the 2026-07-08 Kernel OpenAPI spec, {@code actor-controller}
 * does not document a single-actor-by-id GET endpoint (only {@code POST /api/actors},
 * {@code GET/PUT /api/actors/me}, {@code PUT /api/actors/{actorId}}) — the same gap
 * already found and documented against {@code tnt-resource-core}'s equivalent adapter
 * (see {@code docs/knowledge/known-issues.md} #10). This call may 404 until the Kernel
 * adds one; the fail-open design degrades that to "treat as not found" rather than
 * breaking profile creation.</p>
 *
 * <p>WebClient bean provided by {@code KernelBridgeConfig} (tnt-bootstrap).</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class KernelActorAdapter implements IKernelActorPort {

    private static final Logger log = LoggerFactory.getLogger(KernelActorAdapter.class);

    private final WebClient kernelWebClient;

    public KernelActorAdapter(@Qualifier("kernelWebClient") WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    @Override
    public Mono<KernelActorDto> findById(UUID actorId) {
        var responseSpec = kernelWebClient.get()
                .uri("/api/actors/{id}", actorId)
                .retrieve();
        return KernelResponses.unwrapObject(responseSpec, KernelActorDto.class, log, "findById " + actorId);
    }

    @Override
    public Mono<Boolean> exists(UUID actorId) {
        return findById(actorId)
                .map(dto -> true)
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }
}
