package com.yowyob.tiibntick.core.trust.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.PolVerificationRecord;

/**
 * Outbound Port — {@code PolVerificationRepository}.
 *
 * <p>Persistence contract for {@link PolVerificationRecord} value objects.
 * Implemented by the R2DBC adapter targeting the
 * {@code tnt_trust.pol_verifications} table in the {@code tnt_trust_db} database.
 *
 * <p>This repository is the local PostgreSQL cache for on-chain
 * Proof-of-Location data. It is updated asynchronously when
 * {@code yow.trust.events.committed} notifications arrive from {@code yow-trust-event}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface PolVerificationRepository {

    /**
     * Saves a new {@link PolVerificationRecord}.
     *
     * @param verification the verification record to persist
     * @return a {@link Mono} emitting the saved record
     */
    Mono<PolVerificationRecord> save(PolVerificationRecord verification);

    /**
     * Finds the Proof-of-Location verification history for an actor, most recent first.
     *
     * @param actorId  the actor's unique identifier
     * @param tenantId the tenant identifier
     * @return a {@link Flux} of verification records ordered by {@code verifiedAt} descending
     */
    Flux<PolVerificationRecord> findByActorId(String actorId, String tenantId);

    /**
     * Updates the Fabric transaction hash for a verification after on-chain confirmation.
     * Called by {@link com.yowyob.tiibntick.core.trust.adapter.in.kafka.TrustCommittedEventConsumer}.
     *
     * @param eventId the verification event identifier
     * @param txHash  the Fabric transaction hash
     * @return a {@link Mono} completing when the update is persisted
     */
    Mono<Void> updateTxHash(String eventId, String txHash);
}
