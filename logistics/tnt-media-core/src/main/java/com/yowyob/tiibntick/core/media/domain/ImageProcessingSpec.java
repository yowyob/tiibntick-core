package com.yowyob.tiibntick.core.media.domain;

import lombok.Builder;
import lombok.Getter;

/**
 * Specification for image processing operations applied to uploaded photos
 * (delivery proof, package photos, profile photos).
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public final class ImageProcessingSpec {

    /** Maximum width in pixels. Images wider than this are downscaled. */
    @Builder.Default
    private final int maxWidthPx = 1920;

    /** Maximum height in pixels. Images taller than this are downscaled. */
    @Builder.Default
    private final int maxHeightPx = 1080;

    /** JPEG quality (1-100). Default 80 balances size and visual quality. */
    @Builder.Default
    private final int quality = 80;

    /** When true, a watermark text is overlaid on the image. */
    @Builder.Default
    private final boolean addWatermark = false;

    /** Watermark text (only relevant when {@code addWatermark} is true). */
    private final String watermarkText;

    /** When true, the image is converted to grayscale before storage. */
    @Builder.Default
    private final boolean convertToGrayscale = false;

    /** Default spec for delivery proof photos. */
    public static ImageProcessingSpec deliveryProof() {
        return ImageProcessingSpec.builder()
                .maxWidthPx(1280)
                .maxHeightPx(960)
                .quality(85)
                .addWatermark(true)
                .watermarkText("TiiBnTick — Preuve de livraison")
                .build();
    }

    /** Default spec for KYC documents (grayscale, high quality for readability). */
    public static ImageProcessingSpec kycDocument() {
        return ImageProcessingSpec.builder()
                .maxWidthPx(2480)
                .maxHeightPx(3508)
                .quality(90)
                .convertToGrayscale(true)
                .build();
    }

    /** Default spec for profile photos (small square-ish). */
    public static ImageProcessingSpec profilePhoto() {
        return ImageProcessingSpec.builder()
                .maxWidthPx(512)
                .maxHeightPx(512)
                .quality(80)
                .build();
    }
}
