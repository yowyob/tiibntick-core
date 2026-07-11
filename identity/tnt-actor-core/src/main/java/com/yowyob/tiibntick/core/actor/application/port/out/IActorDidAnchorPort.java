package com.yowyob.tiibntick.core.actor.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Outbound port for issuing an actor's Decentralized Identifier (DID) on the
 * blockchain, once the actor has passed KYC verification.
 *
 * <p>Implemented by an adapter living in {@code tnt-trust-core} (which depends on
 * {@code tnt-actor-core} to see this port — never the other way round, to keep
 * the module graph acyclic). Best-effort: a failure here must never fail the
 * KYC-validation flow, so callers should contain errors before/around this call.
 *
 * @author MANFOUO Braun
 */
public interface IActorDidAnchorPort {

    /**
     * Issues and anchors a DID for a KYC-verified actor.
     *
     * @param payload the actor's identity data to anchor
     * @return a {@link Mono} emitting the issued DID string (format:
     *         {@code did:tiibntick:{tenantId}:{actorId}})
     */
    Mono<String> issueDid(ActorDidAnchorPayload payload);
}
