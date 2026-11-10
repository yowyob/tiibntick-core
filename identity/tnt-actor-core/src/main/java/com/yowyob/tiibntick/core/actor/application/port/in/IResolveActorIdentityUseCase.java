package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.domain.model.ActorIdentitySummary;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Resolves an actor's human-facing identity (display name, phone, email)
 * from the Kernel. Useful to ANY module that needs to show a name instead of
 * a bare UUID — not Link-specific, kept here in tnt-actor-core rather than
 * duplicated per-product.
 */
public interface IResolveActorIdentityUseCase {

    Mono<ActorIdentitySummary> resolve(UUID actorId);

    /**
     * Resolves the identity of the <b>authenticated caller</b> (Kernel {@code GET /api/actors/me},
     * with the caller's bearer). Preferred over {@link #resolve(UUID)} for "my profile" endpoints,
     * since the Kernel has no by-id actor lookup.
     */
    Mono<ActorIdentitySummary> resolveCurrent();
}
