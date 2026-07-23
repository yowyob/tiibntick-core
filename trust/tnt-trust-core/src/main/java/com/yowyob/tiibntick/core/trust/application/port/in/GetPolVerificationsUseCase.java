package com.yowyob.tiibntick.core.trust.application.port.in;

import reactor.core.publisher.Flux;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.PolVerificationRecord;

/**
 * Inbound Port — {@code GetPolVerificationsUseCase}.
 *
 * <p>Retrieves the Proof-of-Location verification history for a TiiBnTick
 * actor. Results sourced from the local PostgreSQL cache (tnt_trust schema).
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface GetPolVerificationsUseCase {

    /**
     * Retrieves the Proof-of-Location verification history for an actor.
     *
     * @param actorId  the actor's unique identifier
     * @param tenantId the tenant identifier
     * @return a {@link Flux} of verification records, most recent first
     */
    Flux<PolVerificationRecord> getByActorId(String actorId, String tenantId);
}
