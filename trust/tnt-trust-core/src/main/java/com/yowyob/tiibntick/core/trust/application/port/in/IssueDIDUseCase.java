package com.yowyob.tiibntick.core.trust.application.port.in;

import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;

/**
 * Inbound Port — {@code IssueDIDUseCase}.
 *
 * <p>Issues, verifies, or revokes a Decentralized Identifier (DID) for
 * a TiiBnTick deliverer actor. All operations are anchored on Hyperledger Fabric.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface IssueDIDUseCase {

    /**
     * Issues a new DID for an actor and anchors it on Fabric.
     *
     * @param actorId      the actor's unique identifier
     * @param tenantId     the tenant identifier
     * @param publicKeyPem the actor's ECDSA P-256 public key in PEM format
     * @return a {@link Mono} emitting the issued {@link DIDDocument}
     */
    Mono<DIDDocument> issue(String actorId, String tenantId, String publicKeyPem);

    /**
     * Revokes an existing DID and records the revocation on Fabric.
     *
     * @param did      the DID string to revoke
     * @param tenantId the tenant identifier
     * @return a {@link Mono} completing when the revocation is recorded
     */
    Mono<Void> revoke(String did, String tenantId);

    /**
     * Verifies whether a DID is valid: not expired, not revoked,
     * and confirmed on-chain.
     *
     * @param did the DID string to verify
     * @return a {@link Mono} emitting {@code true} if valid
     */
    Mono<Boolean> verify(String did);
    /**
     * Issues a new DID for a FreelancerOrganization and anchors it on Fabric ().
     *
     * <p>DID format: {@code did:tiibntick:{tenantId}:org:{orgId}}.
     *
     * @param orgId       the FreelancerOrg UUID (from tnt-organization-core)
     * @param tenantId    the tenant identifier
     * @param tradeName   the FreelancerOrg's commercial trade name
     * @param publicKeyPem the org's ECDSA P-256 public key in PEM format
     * @return a {@link Mono} emitting the issued {@link DIDDocument}
     */
    Mono<DIDDocument> issueForFreelancerOrg(String orgId, String tenantId,
                                             String tradeName, String publicKeyPem);

    /**
     * Revokes an existing DID with an explanatory reason ( extended).
     *
     * @param did      the DID string to revoke
     * @param tenantId the tenant identifier
     * @param reason   the revocation reason (for audit trail)
     * @return a {@link Mono} completing when the revocation is recorded
     */
    default Mono<Void> revokeWithReason(String did, String tenantId, String reason) {
        return revoke(did, tenantId);
    }

}