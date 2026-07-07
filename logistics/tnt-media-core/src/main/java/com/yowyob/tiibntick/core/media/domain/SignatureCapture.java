package com.yowyob.tiibntick.core.media.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Objects;

/**
 * Value object capturing a digital signature drawn by a recipient on delivery.
 * The signature is stored as a Base64-encoded PNG or SVG image.
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public final class SignatureCapture {

    /** Base64-encoded image data of the drawn signature. */
    private final String base64Data;

    /** MIME type of the signature image (image/png or image/svg+xml). */
    @Builder.Default
    private final String mimeType = "image/png";

    /** UTC timestamp when the signature was captured. */
    private final LocalDateTime capturedAt;

    /** User ID of the actor who captured the signature (usually the deliverer). */
    private final String capturedByUserId;

    /** GPS latitude at the time of capture, if available. */
    private final Double gpsLatitude;

    /** GPS longitude at the time of capture, if available. */
    private final Double gpsLongitude;

    /**
     * Converts the Base64 data to a raw byte array suitable for MinIO upload.
     *
     * @return decoded byte array
     * @throws IllegalStateException if base64Data is null or not valid Base64
     */
    public byte[] toBytes() {
        Objects.requireNonNull(base64Data, "base64Data must not be null");
        String cleanData = base64Data.contains(",")
                ? base64Data.substring(base64Data.indexOf(',') + 1)
                : base64Data;
        return Base64.getDecoder().decode(cleanData);
    }

    /**
     * Validates that the capture contains usable signature data.
     *
     * @return {@code true} if the signature is non-null and non-empty
     */
    public boolean isValid() {
        if (base64Data == null || base64Data.isBlank()) {
            return false;
        }
        try {
            byte[] decoded = toBytes();
            return decoded.length > 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
