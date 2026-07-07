package com.yowyob.tiibntick.core.media.domain;

import lombok.Builder;
import lombok.Getter;

import java.util.Objects;

/**
 * Specification for generating a QR code image.
 * Captures all visual and structural parameters required by the QR rendering pipeline.
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder(toBuilder = true)
public final class QRCodeSpec {

    /** The data string to be encoded (canonical QR payload string). */
    private final String payload;

    /** Desired output format. */
    @Builder.Default
    private final QRFormat format = QRFormat.PNG;

    /** Output image width and height in pixels (QR codes are square). */
    @Builder.Default
    private final int sizePx = 300;

    /**
     * ZXing error correction level: L (7%), M (15%), Q (25%), H (30%).
     * Use at least M when a logo is overlaid.
     */
    @Builder.Default
    private final String errorCorrectionLevel = "M";

    /** Foreground color as a hex string (default black). */
    @Builder.Default
    private final String foregroundColor = "#000000";

    /** Background color as a hex string (default white). */
    @Builder.Default
    private final String backgroundColor = "#FFFFFF";

    /**
     * Optional MinIO key of a logo to overlay at the center of the QR code.
     * When set, {@code errorCorrectionLevel} should be "H".
     */
    private final String logoKey;

    /**
     * Validates the specification to ensure it can produce a renderable QR code.
     *
     * @throws IllegalArgumentException if any constraint is violated
     */
    public void validate() {
        Objects.requireNonNull(payload, "QR payload must not be null");
        if (payload.isBlank()) {
            throw new IllegalArgumentException("QR payload must not be blank");
        }
        if (sizePx < 50 || sizePx > 2000) {
            throw new IllegalArgumentException("QR size must be between 50 and 2000 px, got: " + sizePx);
        }
        if (!errorCorrectionLevel.matches("[LMQH]")) {
            throw new IllegalArgumentException("Invalid error correction level: " + errorCorrectionLevel);
        }
    }
}
