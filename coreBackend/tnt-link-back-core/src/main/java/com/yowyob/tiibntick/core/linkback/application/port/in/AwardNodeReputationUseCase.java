package com.yowyob.tiibntick.core.linkback.application.port.in;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Internal port for awarding reputation/gamification changes to whichever
 * {@code NetworkNode} extends the given actor/organization. A no-op (not an
 * error) when the actor has no registered node yet — callers don't need to
 * know whether a node exists before crediting an action.
 */
public interface AwardNodeReputationUseCase {

    Mono<Void> awardTrust(UUID tenantId, UUID refId, double trustDelta);

    Mono<Void> awardPoints(UUID tenantId, UUID refId, int pointsDelta);
}
