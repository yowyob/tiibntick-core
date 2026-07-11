package com.yowyob.tiibntick.core.trust.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;

/**
 * Outbound Port — {@code DIDRepository}.
 *
 * <p>Persistence contract for {@link DIDDocument} aggregates.
 * Implemented by the R2DBC adapter targeting the
 * {@code tnt_trust.did_documents} table in the {@code tnt_trust_db} PostgreSQL database.
 *
 * <p>Acts as a local cache of on-chain DID data, avoiding repeated
 * calls to the Fabric ledger for read-heavy operations.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface DIDRepository {

    /**
     * Saves or updates a {@link DIDDocument}.
     *
     * @param document the DID document to persist
     * @return a {@link Mono} emitting the saved document
     */
    Mono<DIDDocument> save(DIDDocument document);

    /**
     * Finds the active DID document for a given actor.
     *
     * @param actorId  the actor's unique identifier
     * @param tenantId the tenant identifier
     * @return a {@link Mono} emitting the document, or empty if not found
     */
    Mono<DIDDocument> findByActorId(String actorId, String tenantId);

    /**
     * Finds a DID document by its DID string.
     *
     * @param did the DID string (e.g., {@code did:tiibntick:tenant1:actor1})
     * @return a {@link Mono} emitting the document, or empty if not found
     */
    Mono<DIDDocument> findByDID(String did);

    /**
     * Revokes a DID document by updating its status to revoked.
     *
     * @param did the DID string to revoke
     * @return a {@link Mono} completing when the revocation is persisted
     */
    Mono<Void> revokeByDID(String did);

    /**
     * Returns all active (non-revoked, non-expired) DID documents for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return a {@link Flux} of active DID documents
     */
    Flux<DIDDocument> findActiveByTenantId(String tenantId);
}
