package com.yowyob.tiibntick.core.trust.domain.model.valueobject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Value Object — {@code DeliveryProofRecord}.
 *
 * <p>Encapsulates the immutable proof that a package was delivered to the
 * recipient. Contains:
 * <ul>
 *   <li>A photo hash (SHA-256 of the delivery photo uploaded to MinIO)</li>
 *   <li>A recipient signature hash</li>
 *   <li>GPS coordinates at the moment of delivery</li>
 *   <li>The Fabric transaction hash once anchored on-chain</li>
 * </ul>
 *
 * <p>This record is created by {@code tnt-delivery-core} after a successful
 * delivery confirmation and passed to {@code tnt-trust} for blockchain anchoring.
 *
 * <p><strong>No Spring annotations.</strong> Pure domain code.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public final class DeliveryProofRecord {

    private final String proofId;
    private final String missionId;
    private final String packageId;
    private final String actorId;
    private final String tenantId;

    /**
     * SHA-256 hash of the delivery photo stored in MinIO.
     * The hash is computed by the mobile app before upload.
     */
    private final String photoHash;

    /**
     * SHA-256 hash of the recipient's digital signature.
     * Null if the recipient declined to sign.
     */
    private final String signatureHash;

    private final double gpsLat;
    private final double gpsLng;
    private final LocalDateTime confirmedAt;

    /**
     * Proof of Content (PoC): SHA-256 of the canonical delivery proof content.
     * Canonical form: "{missionId}|{packageId}|{actorId}|{photoHash}|{confirmedAt}|{gpsLat}|{gpsLng}"
     * Computed before publishing to Kafka; passed as {@code dataHash} to the chaincode.
     */
    private String pocHash;

    /**
     * Fabric transaction hash — populated after the
     * {@code DELIVERY_PROOF_RECORDED} event is committed to the ledger.
     */
    private String blockchainTxHash;

    // ── : FreelancerOrg executor context ─────────────────────────────────

    /**
     * UUID of the FreelancerOrganization that executed this delivery.
     * Null when executed by an Agency deliverer directly.
     * References tnt-organization-core UUID — pure integration key (no join).
     */
    private final String executorOrgId;

    /**
     * Type of the executing organization: {@code "FREELANCER_ORG"} or {@code "AGENCY"}.
     * Null when executorOrgId is null.
     */
    private final String executorOrgType;

    /**
     * UUID of the SUB_DELIVERER who physically executed the delivery.
     * Null when the FreelancerOrg OWNER executed the delivery directly.
     * References tnt-actor-core UUID — pure integration key.
     */
    private final String subDelivererId;

    public DeliveryProofRecord(
            final String proofId,
            final String missionId,
            final String packageId,
            final String actorId,
            final String tenantId,
            final String photoHash,
            final String signatureHash,
            final double gpsLat,
            final double gpsLng,
            final LocalDateTime confirmedAt) {
        this(proofId, missionId, packageId, actorId, tenantId,
                photoHash, signatureHash, gpsLat, gpsLng, confirmedAt, null, null, null);
    }

    /**
     * Full constructor including  FreelancerOrg executor context.
     *
     * @param executorOrgId   UUID of the executing FreelancerOrg (null for Agency)
     * @param executorOrgType "FREELANCER_ORG" or "AGENCY" (null when not set)
     * @param subDelivererId  UUID of the sub-deliverer (null if OWNER executed directly)
     */
    public DeliveryProofRecord(
            final String proofId,
            final String missionId,
            final String packageId,
            final String actorId,
            final String tenantId,
            final String photoHash,
            final String signatureHash,
            final double gpsLat,
            final double gpsLng,
            final LocalDateTime confirmedAt,
            final String executorOrgId,
            final String executorOrgType,
            final String subDelivererId) {
        this.proofId = Objects.requireNonNull(proofId, "proofId must not be null");
        this.missionId = Objects.requireNonNull(missionId, "missionId must not be null");
        this.packageId = Objects.requireNonNull(packageId, "packageId must not be null");
        this.actorId = Objects.requireNonNull(actorId, "actorId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.photoHash = Objects.requireNonNull(photoHash, "photoHash must not be null");
        this.signatureHash = signatureHash;
        this.gpsLat = gpsLat;
        this.gpsLng = gpsLng;
        this.confirmedAt = Objects.requireNonNull(confirmedAt, "confirmedAt must not be null");
        this.executorOrgId = executorOrgId;
        this.executorOrgType = executorOrgType;
        this.subDelivererId = subDelivererId;
    }

    /**
     * Computes the canonical SHA-256 data hash for this delivery proof.
     * This hash is the fingerprint submitted to Fabric via
     * {@code yow-trust-event} and stored in {@code BlockchainProof.dataHash}.
     *
     * <p>Formula: SHA-256(proofId + "|" + missionId + "|" + packageId
     *              + "|" + actorId + "|" + photoHash + "|" + confirmedAt)
     *
     * @return the hex-encoded data hash (64 chars)
     */
    public String computeDataHash() {
        try {
            final String raw = proofId + "|" + missionId + "|" + packageId
                    + "|" + actorId + "|" + photoHash + "|" + confirmedAt;
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Returns {@code true} if this proof has been anchored on Fabric
     * and can be externally verified using the given {@code txHash}.
     *
     * @param txHash the Fabric transaction hash to check against
     * @return {@code true} if verifiable
     */
    public boolean isVerifiable(final String txHash) {
        return blockchainTxHash != null && blockchainTxHash.equals(txHash);
    }

    /** Records the Fabric tx hash after on-chain confirmation. */
    public void confirmOnChain(final String txHash) {
        this.blockchainTxHash = Objects.requireNonNull(txHash);
    }

    public String getProofId() { return proofId; }
    public String getMissionId() { return missionId; }
    public String getPackageId() { return packageId; }
    public String getActorId() { return actorId; }
    public String getTenantId() { return tenantId; }
    public String getPhotoHash() { return photoHash; }
    public String getSignatureHash() { return signatureHash; }
    public double getGpsLat() { return gpsLat; }
    public double getGpsLng() { return gpsLng; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public String getPocHash() { return pocHash; }
    public void setPocHash(final String pocHash) { this.pocHash = pocHash; }
    public String getExecutorOrgId() { return executorOrgId; }
    public String getExecutorOrgType() { return executorOrgType; }
    public String getSubDelivererId() { return subDelivererId; }
    public String getBlockchainTxHash() { return blockchainTxHash; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof DeliveryProofRecord other)) return false;
        return Objects.equals(proofId, other.proofId);
    }

    @Override
    public int hashCode() { return Objects.hash(proofId); }
}
