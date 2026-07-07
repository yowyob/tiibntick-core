package com.yowyob.tiibntick.core.media.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing the data embedded inside a TiiBnTick QR code.
 * <p>
 * The payload is HMAC-SHA256 signed to prevent forgery.
 * Format: {@code TNT:{version}:{trackingCode}:{missionId}:{timestamp}:{signature}}
 * <p>
 * The {@code signature} field is computed over the concatenation of all other fields
 * using a per-tenant HMAC key stored in application configuration.
 * <p>
 * This VO is immutable and self-validating.
 *
 * @author MANFOUO Braun
 */
@Getter
public final class QrPayload {

    private static final String QR_VERSION = "1";
    private static final String PREFIX = "TNT";

    /** Unique tracking code of the package (e.g., TNT-CMR-2026-000123). */
    private final String trackingCode;

    /** UUID of the Mission this package belongs to. */
    private final UUID missionId;

    /** Package UUID (may be null for mission-level QRs). */
    private final UUID packageId;

    /** Tenant identifier — used to look up the correct HMAC key. */
    private final String tenantId;

    /** Epoch-second timestamp at which this payload was generated. */
    private final long issuedAt;

    /**
     * HMAC-SHA256 signature over {@code version + trackingCode + missionId + packageId + issuedAt}.
     * Populated after signing; null in unsigned in-memory instances.
     */
    private final String hmacSignature;

    @JsonCreator
    private QrPayload(
            @JsonProperty("trackingCode") String trackingCode,
            @JsonProperty("missionId") UUID missionId,
            @JsonProperty("packageId") UUID packageId,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("issuedAt") long issuedAt,
            @JsonProperty("hmacSignature") String hmacSignature) {
        this.trackingCode = Objects.requireNonNull(trackingCode, "trackingCode");
        this.missionId = Objects.requireNonNull(missionId, "missionId");
        this.packageId = packageId;
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.issuedAt = issuedAt;
        this.hmacSignature = hmacSignature;
    }

    public static QrPayload of(String trackingCode, UUID missionId, UUID packageId, String tenantId) {
        return new QrPayload(trackingCode, missionId, packageId, tenantId, Instant.now().getEpochSecond(), null);
    }

    public QrPayload withSignature(String hmacSignature) {
        return new QrPayload(trackingCode, missionId, packageId, tenantId, issuedAt, hmacSignature);
    }

    /**
     * Serializes the payload into its canonical string form suitable for QR code encoding.
     * Format: {@code TNT:1:{trackingCode}:{missionId}:{packageId}:{tenantId}:{issuedAt}:{signature}}
     *
     * @return canonical payload string
     * @throws IllegalStateException if the payload has not been signed
     */
    public String toQRCodeString() {
        if (hmacSignature == null) {
            throw new IllegalStateException("QrPayload must be signed before encoding into a QR code");
        }
        return String.join(":",
                PREFIX, QR_VERSION,
                trackingCode,
                missionId.toString(),
                packageId != null ? packageId.toString() : "null",
                tenantId,
                String.valueOf(issuedAt),
                hmacSignature);
    }

    /**
     * Builds the string that is signed by the HMAC function (excludes the signature itself).
     *
     * @return the message to be signed
     */
    public String signingMessage() {
        return String.join(":",
                PREFIX, QR_VERSION,
                trackingCode,
                missionId.toString(),
                packageId != null ? packageId.toString() : "null",
                tenantId,
                String.valueOf(issuedAt));
    }

    /**
     * Parses a canonical QR code string back into a {@link QrPayload}.
     *
     * @param raw raw string decoded from a QR code scan
     * @return parsed payload
     * @throws IllegalArgumentException if the string format is invalid
     */
    public static QrPayload parse(String raw) {
        String[] parts = raw.split(":");
        if (parts.length < 8 || !PREFIX.equals(parts[0]) || !QR_VERSION.equals(parts[1])) {
            throw new IllegalArgumentException("Invalid QR payload format: " + raw);
        }
        String trackingCode = parts[2];
        UUID missionId = UUID.fromString(parts[3]);
        UUID packageId = "null".equals(parts[4]) ? null : UUID.fromString(parts[4]);
        String tenantId = parts[5];
        long issuedAt = Long.parseLong(parts[6]);
        String signature = parts[7];
        return new QrPayload(trackingCode, missionId, packageId, tenantId, issuedAt, signature);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QrPayload that)) return false;
        return issuedAt == that.issuedAt &&
               Objects.equals(trackingCode, that.trackingCode) &&
               Objects.equals(missionId, that.missionId) &&
               Objects.equals(packageId, that.packageId) &&
               Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trackingCode, missionId, packageId, tenantId, issuedAt);
    }
}
