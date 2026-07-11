package com.yowyob.tiibntick.core.trust.domain.model.valueobject;

import com.yowyob.tiibntick.core.trust.domain.model.enums.CustodyTransferType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Value Object — {@code CustodyTransferRecord}.
 *
 * <p>Represents an immutable record of a package custody transfer between
 * two TiiBnTick actors. When anchored on Hyperledger Fabric, it forms
 * part of the package's "Fil d'Ariane" (chain of custody audit trail).
 *
 * <p>Together, ordered CustodyTransferRecord instances form a
 * {@link ParcelCustodyChain}. The Proof of Integrity (PoI) chain is
 * maintained via:
 * <ul>
 *   <li>{@code previousCustodyHash}: hash of the preceding custody transfer</li>
 *   <li>{@code custodyHash}: SHA-256 of this transfer's canonical content</li>
 * </ul>
 *
 * <p>Created by {@code tnt-delivery-core} when a package changes hands and
 * passed to {@code tnt-trust} for blockchain anchoring.
 *
 * <p><strong>No Spring annotations.</strong> Pure domain code.
 *
 * @author MANFOUO Braun
 * @version 1.1
 */
public final class CustodyTransferRecord {

    private final String transferId;
    private final String packageId;
    private final String trackingCode;
    private final String tenantId;

    /**
     * Actor transferring custody (sender, deliverer, hub operator).
     * Null for the first transfer (originating from sender).
     */
    private final String fromActorId;

    /** Actor receiving custody (deliverer, hub operator, recipient). */
    private final String toActorId;

    private final CustodyTransferType transferType;

    /**
     * Hub identifier — populated when the transfer involves a relay hub
     * (i.e., {@link CustodyTransferType#TRANSFER_TO_HUB} or
     * {@link CustodyTransferType#PICKUP_FROM_HUB}).
     */
    private final String hubId;

    /** GPS latitude of the transfer location. */
    private final Double gpsLat;

    /** GPS longitude of the transfer location. */
    private final Double gpsLng;

    private final LocalDateTime transferredAt;

    /**
     * Proof of Content (PoC): SHA-256 of the canonical transfer content.
     * Canonical form: "{packageId}|{fromActorId}|{toActorId}|{transferType}|{transferredAt}|{gpsLat}|{gpsLng}"
     * Allows external verification without re-querying the blockchain.
     */
    private String pocHash;

    /**
     * Proof of Integrity (PoI): hash of the PREVIOUS custody transfer in the chain.
     * "0000...0000" (64 zeros) for the first transfer (genesis link).
     */
    private String previousCustodyHash;

    /**
     * The proofHash of THIS custody transfer, computed by the chaincode.
     */
    private String custodyHash;

    /**
     * Fabric transaction hash confirming this transfer on-chain.
     * Populated by {@link #confirmOnChain(String)}.
     */
    private String blockchainTxHash;

    /** Backward-compatible constructor (no GPS / provenance fields). */
    public CustodyTransferRecord(
            final String transferId,
            final String packageId,
            final String trackingCode,
            final String tenantId,
            final String fromActorId,
            final String toActorId,
            final CustodyTransferType transferType,
            final String hubId,
            final LocalDateTime transferredAt) {
        this(transferId, packageId, trackingCode, tenantId,
                fromActorId, toActorId, transferType, hubId,
                null, null, transferredAt);
    }

    /** Full constructor including GPS coordinates. */
    public CustodyTransferRecord(
            final String transferId,
            final String packageId,
            final String trackingCode,
            final String tenantId,
            final String fromActorId,
            final String toActorId,
            final CustodyTransferType transferType,
            final String hubId,
            final Double gpsLat,
            final Double gpsLng,
            final LocalDateTime transferredAt) {
        this.transferId = transferId;
        this.packageId = Objects.requireNonNull(packageId, "packageId must not be null");
        this.trackingCode = trackingCode;
        this.tenantId = tenantId;
        this.fromActorId = fromActorId;
        this.toActorId = toActorId;
        this.transferType = transferType;
        this.hubId = hubId;
        this.gpsLat = gpsLat;
        this.gpsLng = gpsLng;
        this.transferredAt = transferredAt;
    }

    /**
     * Computes the Proof of Content (PoC) hash for a custody transfer.
     *
     * <p>Canonical form:
     * {@code "{packageId}|{fromActorId}|{toActorId}|{transferType}|{transferredAt}|{gpsLat}|{gpsLng}"}
     *
     * <p>Called before publishing to Kafka; the caller passes the computed hash
     * to the chaincode as {@code dataHash}.
     */
    public static String computePocHash(
            final String packageId,
            final String fromActorId,
            final String toActorId,
            final String transferType,
            final String transferredAt,
            final Double gpsLat,
            final Double gpsLng) {
        final String canonical = String.format("%s|%s|%s|%s|%s|%s|%s",
                packageId,
                fromActorId != null ? fromActorId : "",
                toActorId,
                transferType,
                transferredAt,
                gpsLat != null ? gpsLat.toString() : "",
                gpsLng != null ? gpsLng.toString() : "");
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Records the Fabric tx hash after on-chain confirmation. */
    public void confirmOnChain(final String txHash) {
        this.blockchainTxHash = Objects.requireNonNull(txHash);
    }

    public String getTransferId() { return transferId; }
    public String getPackageId() { return packageId; }
    public String getTrackingCode() { return trackingCode; }
    public String getTenantId() { return tenantId; }
    public String getFromActorId() { return fromActorId; }
    public String getToActorId() { return toActorId; }
    public CustodyTransferType getTransferType() { return transferType; }
    public String getHubId() { return hubId; }
    public Double getGpsLat() { return gpsLat; }
    public Double getGpsLng() { return gpsLng; }
    public LocalDateTime getTransferredAt() { return transferredAt; }
    public String getPocHash() { return pocHash; }
    public void setPocHash(final String pocHash) { this.pocHash = pocHash; }
    public String getPreviousCustodyHash() { return previousCustodyHash; }
    public void setPreviousCustodyHash(final String previousCustodyHash) { this.previousCustodyHash = previousCustodyHash; }
    public String getCustodyHash() { return custodyHash; }
    public void setCustodyHash(final String custodyHash) { this.custodyHash = custodyHash; }
    public String getBlockchainTxHash() { return blockchainTxHash; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof CustodyTransferRecord other)) return false;
        return Objects.equals(transferId, other.transferId);
    }

    @Override
    public int hashCode() { return Objects.hash(transferId); }
}
