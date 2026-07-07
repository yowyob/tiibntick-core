package com.yowyob.tiibntick.core.media.service;

import com.yowyob.tiibntick.core.media.domain.ImageProcessingSpec;
import com.yowyob.tiibntick.core.media.domain.MediaFile;
import com.yowyob.tiibntick.core.media.domain.MediaFileId;
import com.yowyob.tiibntick.core.media.domain.MediaType;
import com.yowyob.tiibntick.core.media.domain.exception.MediaFileNotFoundException;
import com.yowyob.tiibntick.core.media.port.inbound.IUploadMediaUseCase;
import com.yowyob.tiibntick.core.media.port.outbound.IMediaRepository;
import com.yowyob.tiibntick.core.media.port.outbound.IObjectStorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HexFormat;

/**
 * Application service implementing generic media upload lifecycle.
 * Handles SHA-256 deduplication, optional image processing, MinIO upload,
 * and PostgreSQL metadata persistence.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService implements IUploadMediaUseCase {

    private static final int DEFAULT_PRESIGNED_TTL = 3600;

    private final IObjectStorageClient storageClient;
    private final IMediaRepository mediaRepository;

    @Override
    public Mono<MediaFile> upload(
            String tenantId, String ownerUserId, MediaType type,
            String mimeType, String originalFileName, byte[] data, boolean isPublic) {

        return Mono.fromCallable(() -> sha256Hex(data))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(hash -> mediaRepository.findByHashAndTenant(hash, tenantId)
                        .switchIfEmpty(Mono.defer(() -> performUpload(tenantId, ownerUserId, type, mimeType,
                                originalFileName, data, isPublic, hash))));
    }

    @Override
    public Mono<MediaFile> uploadImage(
            String tenantId, String ownerUserId, MediaType type,
            String originalFileName, byte[] imageData, ImageProcessingSpec spec) {

        return Mono.fromCallable(() -> processImage(imageData, spec))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(processed -> upload(tenantId, ownerUserId, type, "image/jpeg",
                        originalFileName, processed, false));
    }

    @Override
    public Mono<String> generatePresignedUrl(MediaFileId fileId, int ttlSeconds) {
        return mediaRepository.findById(fileId)
                .switchIfEmpty(Mono.error(new MediaFileNotFoundException(fileId)))
                .flatMap(file -> storageClient.presignedGetUrl(
                        file.getStorageBucket(), file.getStorageKey(),
                        ttlSeconds > 0 ? ttlSeconds : DEFAULT_PRESIGNED_TTL));
    }

    @Override
    public Mono<Void> delete(MediaFileId fileId, String tenantId) {
        return mediaRepository.findById(fileId)
                .flatMap(file -> storageClient.delete(file.getStorageBucket(), file.getStorageKey())
                        .then(mediaRepository.deleteById(fileId)))
                .switchIfEmpty(Mono.empty());
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private Mono<MediaFile> performUpload(
            String tenantId, String ownerUserId, MediaType type,
            String mimeType, String originalFileName, byte[] data,
            boolean isPublic, String hash) {

        String bucket = MediaFile.bucketNameFor(tenantId);
        String key = buildObjectKey(type, originalFileName, hash);
        LocalDateTime expiresAt = computeExpiry(type);

        return storageClient.ensureBucketExists(bucket)
                .then(storageClient.upload(bucket, key, data, mimeType))
                .then(Mono.fromCallable(() -> MediaFile.create(
                        tenantId, ownerUserId, type, mimeType, originalFileName,
                        bucket, key, data.length, hash, isPublic, expiresAt, Collections.emptyMap())))
                .flatMap(mediaRepository::save);
    }

    private byte[] processImage(byte[] rawData, ImageProcessingSpec spec) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(rawData));
            if (src == null) {
                throw new IllegalArgumentException("Cannot decode image data");
            }

            // Resize if needed
            int newW = Math.min(src.getWidth(), spec.getMaxWidthPx());
            int newH = Math.min(src.getHeight(), spec.getMaxHeightPx());
            double scale = Math.min((double) newW / src.getWidth(), (double) newH / src.getHeight());
            newW = (int) (src.getWidth() * scale);
            newH = (int) (src.getHeight() * scale);

            int imageType = spec.isConvertToGrayscale()
                    ? BufferedImage.TYPE_BYTE_GRAY
                    : BufferedImage.TYPE_INT_RGB;

            BufferedImage output = new BufferedImage(newW, newH, imageType);
            Graphics2D g = output.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawRenderedImage(src, AffineTransform.getScaleInstance(scale, scale));

            if (spec.isAddWatermark() && spec.getWatermarkText() != null) {
                g.setColor(new java.awt.Color(255, 255, 255, 100));
                g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
                g.drawString(spec.getWatermarkText(), 10, newH - 10);
            }
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(output, "JPEG", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Image processing failed", e);
        }
    }

    private String buildObjectKey(MediaType type, String originalFileName, String hash) {
        String prefix = type.name().toLowerCase().replace('_', '/');
        String ext = extractExtension(originalFileName);
        return prefix + "/" + hash.substring(0, 8) + "_" + System.currentTimeMillis() + ext;
    }

    private String extractExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : "";
    }

    private LocalDateTime computeExpiry(MediaType type) {
        return switch (type) {
            case QR_CODE, DELIVERY_PROOF_PHOTO, SIGNATURE -> LocalDateTime.now().plusYears(7);
            case INVOICE_PDF, DELIVERY_MANIFEST -> LocalDateTime.now().plusYears(10);
            case PROFILE_PHOTO, PACKAGE_PHOTO -> LocalDateTime.now().plusYears(2);
            default -> LocalDateTime.now().plusYears(5);
        };
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
