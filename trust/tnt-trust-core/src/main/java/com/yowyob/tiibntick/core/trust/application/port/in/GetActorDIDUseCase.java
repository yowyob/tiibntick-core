package com.yowyob.tiibntick.core.trust.application.port.in;

import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;

/**
 * Inbound Port — {@code GetActorDIDUseCase}.
 *
 * <p>Retrieves and verifies DID documents and reputation badges for
 * TiiBnTick actors. Results sourced from local PostgreSQL cache
 * (tnt_trust schema) with optional on-chain verification.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface GetActorDIDUseCase {

    /**
     * Retrieves the DID document for an actor.
     *
     * @param actorId  the actor's unique identifier
     * @param tenantId the tenant identifier
     * @return a {@link Mono} emitting the {@link DIDDocument}, or empty if none
     */
    Mono<DIDDocument> getByActorId(String actorId, String tenantId);

    /**
     * Verifies whether an actor holds a specific badge type.
     * Checks the local DID cache and optionally confirms on-chain.
     *
     * @param actorId   the actor's unique identifier
     * @param badgeType the badge type to verify (e.g., "100_DELIVERIES")
     * @param tenantId  the tenant identifier
     * @return a {@link Mono} emitting {@code true} if the badge is confirmed
     */
    Mono<Boolean> verifyBadge(String actorId, String badgeType, String tenantId);
}
