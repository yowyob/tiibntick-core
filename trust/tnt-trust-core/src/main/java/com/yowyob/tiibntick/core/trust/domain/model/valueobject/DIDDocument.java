package com.yowyob.tiibntick.core.trust.domain.model.valueobject;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object — {@code DIDDocument}.
 *
 * <p>Represents a W3C-compatible Decentralized Identifier (DID) document
 * issued to a TiiBnTick deliverer actor. DIDs allow actors to prove their
 * identity without relying on a central authority, using cryptographic keys
 * anchored on the Hyperledger Fabric ledger.
 *
 * <h3>DID Format</h3>
 * <p>DIDs follow the pattern:
 * {@code did:tiibntick:{tenantId}:{actorId}}
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   issue(actorId, tenantId, pubKey) → DIDDocument (ACTIVE)
 *     → recorded on Fabric via LogisticTrustEvent (DELIVERER_DID_ISSUED)
 *   revoke(did) → DIDDocument (REVOKED)
 *     → recorded on Fabric via LogisticTrustEvent (DELIVERER_DID_REVOKED)
 * </pre>
 *
 * <p><strong>No Spring annotations.</strong> Pure domain code.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public final class DIDDocument {

    /** DID method for TiiBnTick: {@code did:tiibntick:...} */
    public static final String DID_METHOD = "did:tiibntick";

    private final String did;
    private final String actorId;
    private final String tenantId;

    /**
     * The actor's public key in PEM format (ECDSA P-256).
     * Used to verify cryptographic signatures in delivery proofs and PoL.
     */
    private final String publicKeyPem;

    /**
     * Service endpoint URL for this actor's identity service.
     * Example: {@code https://api.tiibntick.com/actors/{actorId}/identity}
     */
    private final String serviceEndpoint;

    private final LocalDateTime issuedAt;

    /**
     * Expiry date of this DID. After expiry, the DID must be renewed.
     * Default validity: 1 year from issuance.
     */
    private final LocalDateTime expiresAt;

    /**
     * The Fabric transaction hash confirming this DID's anchoring on-chain.
     * Null until the corresponding {@code DELIVERER_DID_ISSUED} event is committed.
     */
    private String blockchainTxHash;

    /**
     * Whether this DID has been revoked.
     * Once revoked, a DID cannot be reactivated — a new one must be issued.
     */
    private boolean revoked;

    private LocalDateTime revokedAt;

    // ── : Multi-subject DID support ──────────────────────────────────────

    /**
     * Type of the DID subject.
     * Values: {@code "ACTOR"} (default) | {@code "AGENCY"} | {@code "FREELANCER_ORG"}.
     * Null defaults to "ACTOR" for backward compatibility.
     */
    private final String subjectType;

    /**
     * UUID of the organization that owns this DID (for AGENCY and FREELANCER_ORG subjects).
     * Null for ACTOR-type DIDs.
     * References tnt-organization-core UUID — pure integration key (no join).
     */
    private final String orgId;

    private DIDDocument(
            final String did,
            final String actorId,
            final String tenantId,
            final String publicKeyPem,
            final String serviceEndpoint,
            final LocalDateTime issuedAt,
            final LocalDateTime expiresAt,
            final String blockchainTxHash,
            final boolean revoked,
            final LocalDateTime revokedAt,
            final String subjectType,
            final String orgId) {
        this.did = Objects.requireNonNull(did, "did must not be null");
        this.actorId = Objects.requireNonNull(actorId, "actorId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.publicKeyPem = Objects.requireNonNull(publicKeyPem, "publicKeyPem must not be null");
        this.serviceEndpoint = serviceEndpoint;
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.blockchainTxHash = blockchainTxHash;
        this.revoked = revoked;
        this.revokedAt = revokedAt;
        this.subjectType = subjectType != null ? subjectType : "ACTOR";
        this.orgId = orgId;
    }

    // ── Factory Methods ───────────────────────────────────────────────────────

    /**
     * Issues a new {@link DIDDocument} for a TiiBnTick actor.
     * The DID is computed deterministically from the tenantId and actorId.
     *
     * @param actorId       the actor's unique identifier
     * @param tenantId      the tenant identifier
     * @param publicKeyPem  the actor's ECDSA P-256 public key in PEM format
     * @param serviceEndpoint optional service endpoint URL for identity resolution
     * @return a new {@link DIDDocument} valid for 1 year
     */
    public static DIDDocument issue(
            final String actorId,
            final String tenantId,
            final String publicKeyPem,
            final String serviceEndpoint) {
        final String did = buildDID(tenantId, actorId);
        final LocalDateTime now = LocalDateTime.now();
        return new DIDDocument(did, actorId, tenantId, publicKeyPem,
                serviceEndpoint, now, now.plusYears(1), null, false, null, "ACTOR", null);
    }

    /**
     * Issues a new DID for a FreelancerOrganization ().
     *
     * <p>The DID uses the FreelancerOrg UUID as the subject identifier.
     * DID format: {@code did:tiibntick:{tenantId}:org:{orgId}}.
     *
     * @param orgId       the FreelancerOrg UUID (from tnt-organization-core)
     * @param tenantId    the tenant identifier
     * @param tradeName   the FreelancerOrg's commercial trade name
     * @param publicKeyPem the org's ECDSA P-256 public key in PEM format
     * @return a new {@link DIDDocument} with subjectType="FREELANCER_ORG"
     */
    public static DIDDocument issueForFreelancerOrg(
            final String orgId,
            final String tenantId,
            final String tradeName,
            final String publicKeyPem) {
        Objects.requireNonNull(orgId, "orgId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(publicKeyPem, "publicKeyPem must not be null");
        final String did = DID_METHOD + ":" + tenantId + ":org:" + orgId;
        final String serviceEndpoint = "https://api.tiibntick.com/orgs/" + orgId + "/identity";
        final LocalDateTime now = LocalDateTime.now();
        return new DIDDocument(did, orgId, tenantId, publicKeyPem,
                serviceEndpoint, now, now.plusYears(2), null, false, null,
                "FREELANCER_ORG", orgId);
    }

    /**
     * Reconstitutes a {@link DIDDocument} from persisted state.
     */
    public static DIDDocument reconstitute(
            final String did,
            final String actorId,
            final String tenantId,
            final String publicKeyPem,
            final String serviceEndpoint,
            final LocalDateTime issuedAt,
            final LocalDateTime expiresAt,
            final String blockchainTxHash,
            final boolean revoked,
            final LocalDateTime revokedAt) {
        return new DIDDocument(did, actorId, tenantId, publicKeyPem,
                serviceEndpoint, issuedAt, expiresAt, blockchainTxHash, revoked, revokedAt,
                null, null);
    }

    /**
     * Full reconstitution including  FreelancerOrg fields.
     */
    public static DIDDocument reconstituteFull(
            final String did, final String actorId, final String tenantId,
            final String publicKeyPem, final String serviceEndpoint,
            final LocalDateTime issuedAt, final LocalDateTime expiresAt,
            final String blockchainTxHash, final boolean revoked,
            final LocalDateTime revokedAt, final String subjectType, final String orgId) {
        return new DIDDocument(did, actorId, tenantId, publicKeyPem,
                serviceEndpoint, issuedAt, expiresAt, blockchainTxHash, revoked, revokedAt,
                subjectType, orgId);
    }

    // ── Domain Behavior ───────────────────────────────────────────────────────

    /**
     * Records the Fabric transaction hash after the DID issuance event is committed.
     *
     * @param txHash the Fabric transaction hash
     */
    public void confirmOnChain(final String txHash) {
        Objects.requireNonNull(txHash, "txHash must not be null");
        this.blockchainTxHash = txHash;
    }

    /**
     * Revokes this DID. Once revoked, the DID is permanently inactive.
     *
     * @throws IllegalStateException if the DID is already revoked
     */
    public void revoke() {
        if (this.revoked) {
            throw new IllegalStateException("DID is already revoked: " + did);
        }
        this.revoked = true;
        this.revokedAt = LocalDateTime.now();
    }

    /**
     * Returns {@code true} if this DID is currently valid:
     * not expired, not revoked, and confirmed on-chain.
     */
    public boolean isVerifiable() {
        return !revoked && !isExpired() && blockchainTxHash != null;
    }

    /**
     * Returns {@code true} if this DID's validity period has expired.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Returns the canonical DID string representation.
     * Format: {@code did:tiibntick:{tenantId}:{actorId}}
     */
    public String toDIDString() {
        return did;
    }

    // ── Static Helpers ────────────────────────────────────────────────────────

    /**
     * Builds the canonical DID string for a TiiBnTick actor.
     *
     * @param tenantId the tenant identifier
     * @param actorId  the actor identifier
     * @return the DID string
     */
    public static String buildDID(final String tenantId, final String actorId) {
        return DID_METHOD + ":" + tenantId + ":" + actorId;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getDid() { return did; }
    public String getActorId() { return actorId; }
    public String getTenantId() { return tenantId; }
    public String getPublicKeyPem() { return publicKeyPem; }
    public String getServiceEndpoint() { return serviceEndpoint; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public String getBlockchainTxHash() { return blockchainTxHash; }
    public boolean isRevoked() { return revoked; }
    //  getters
    public String getSubjectType() { return subjectType; }
    public String getOrgId() { return orgId; }
    public boolean isFreelancerOrgDID() { return "FREELANCER_ORG".equals(subjectType); }
    public LocalDateTime getRevokedAt() { return revokedAt; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof DIDDocument other)) return false;
        return Objects.equals(did, other.did);
    }

    @Override
    public int hashCode() { return Objects.hash(did); }

    @Override
    public String toString() {
        return "DIDDocument{did='" + did + "', actorId='" + actorId
                + "', revoked=" + revoked + ", verifiable=" + isVerifiable() + "}";
    }
}
