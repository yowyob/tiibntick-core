package com.yowyob.tiibntick.core.media.service;

import com.yowyob.tiibntick.core.media.domain.MediaFile;
import com.yowyob.tiibntick.core.media.domain.MediaFileId;
import com.yowyob.tiibntick.core.media.domain.MediaType;
import com.yowyob.tiibntick.core.media.domain.SignatureCapture;
import com.yowyob.tiibntick.core.media.domain.exception.MediaFileNotFoundException;
import com.yowyob.tiibntick.core.media.port.inbound.ICaptureSignatureUseCase;
import com.yowyob.tiibntick.core.media.port.outbound.IMediaRepository;
import com.yowyob.tiibntick.core.media.port.outbound.IObjectStorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

/**
 * Application service managing digital signature capture during parcel delivery.
 * Validates the signature data, stores it in MinIO, and persists metadata.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalSignatureService implements ICaptureSignatureUseCase {

    private final IObjectStorageClient storageClient;
    private final IMediaRepository mediaRepository;

    @Override
    public Mono<MediaFileId> captureDeliverySignature(
            String tenantId,
            String missionId,
            String recipientName,
            SignatureCapture capture) {

        if (!capture.isValid()) {
            return Mono.error(new IllegalArgumentException("Signature capture data is invalid or empty"));
        }

        return Mono.fromCallable(capture::toBytes)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(bytes -> {
                    String bucket = MediaFile.bucketNameFor(tenantId);
                    String key = buildSignatureKey(missionId);
                    String contentType = capture.getMimeType();

                    return storageClient.ensureBucketExists(bucket)
                            .then(storageClient.upload(bucket, key, bytes, contentType))
                            .then(persistSignatureMetadata(
                                    tenantId, missionId, recipientName,
                                    bucket, key, bytes.length, capture))
                            .map(MediaFile::getId);
                });
    }

    @Override
    public Mono<String> getSignatureKey(MediaFileId fileId, String tenantId) {
        return mediaRepository.findById(fileId)
                .switchIfEmpty(Mono.error(new MediaFileNotFoundException(fileId)))
                .map(MediaFile::getStorageKey);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String buildSignatureKey(String missionId) {
        return "signatures/" + missionId + "/sig_" + System.currentTimeMillis() + ".png";
    }

    private Mono<MediaFile> persistSignatureMetadata(
            String tenantId, String missionId, String recipientName,
            String bucket, String key, int size, SignatureCapture capture) {

        String hash = sha256Hex(key.getBytes());
        Map<String, String> metadata = Map.of(
                "missionId", missionId,
                "recipientName", recipientName,
                "capturedBy", capture.getCapturedByUserId() != null ? capture.getCapturedByUserId() : "unknown",
                "capturedAt", capture.getCapturedAt().toString());

        MediaFile file = MediaFile.create(
                tenantId, null, MediaType.SIGNATURE,
                capture.getMimeType(), key.substring(key.lastIndexOf('/') + 1),
                bucket, key, size, hash,
                false, LocalDateTime.now().plusYears(7), metadata);

        return mediaRepository.save(file);
    }

    private String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
