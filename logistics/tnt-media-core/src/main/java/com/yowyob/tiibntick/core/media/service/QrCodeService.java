package com.yowyob.tiibntick.core.media.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.yowyob.tiibntick.core.media.config.MediaCoreProperties;
import com.yowyob.tiibntick.core.media.domain.MediaFile;
import com.yowyob.tiibntick.core.media.domain.MediaType;
import com.yowyob.tiibntick.core.media.domain.QRCodeSpec;
import com.yowyob.tiibntick.core.media.domain.QrPayload;
import com.yowyob.tiibntick.core.media.domain.exception.QrSignatureInvalidException;
import com.yowyob.tiibntick.core.media.port.inbound.IGenerateQrCodeUseCase;
import com.yowyob.tiibntick.core.media.port.outbound.IMediaRepository;
import com.yowyob.tiibntick.core.media.port.outbound.IObjectStorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * Application service implementing QR code generation and HMAC signature verification
 * for TiiBnTick package tracking.
 * <p>
 * Uses Google ZXing for QR code rendering and HMAC-SHA256 for payload signing.
 * All blocking I/O (ZXing rendering) is offloaded to the bounded elastic scheduler.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrCodeService implements IGenerateQrCodeUseCase {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String QR_MIME_TYPE = "image/png";

    private final IObjectStorageClient storageClient;
    private final IMediaRepository mediaRepository;
    private final MediaCoreProperties properties;

    @Override
    public Mono<String> generate(QrPayload payload, QRCodeSpec spec) {
        QRCodeSpec effectiveSpec = spec.toBuilder()
                .payload(payload.signingMessage())
                .build();
        effectiveSpec.validate();
        return Mono.fromCallable(() -> sign(payload))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(signedPayload -> Mono.fromCallable(() -> renderQrPng(signedPayload, spec))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(pngBytes -> {
                            String bucket = MediaFile.bucketNameFor(payload.getTenantId());
                            String key = buildQrKey(payload);
                            return storageClient.ensureBucketExists(bucket)
                                    .then(storageClient.upload(bucket, key, pngBytes, QR_MIME_TYPE))
                                    .then(persistMetadata(payload, bucket, key, pngBytes.length))
                                    .thenReturn(key);
                        }));
    }

    @Override
    public Mono<QrPayload> verify(String rawQrData, String tenantId) {
        return Mono.fromCallable(() -> {
            QrPayload parsed = QrPayload.parse(rawQrData);
            if (!parsed.getTenantId().equals(tenantId)) {
                throw new QrSignatureInvalidException(
                    "QR tenantId mismatch: expected " + tenantId + " got " + parsed.getTenantId());
            }
            String expectedSig = computeHmac(parsed.signingMessage(), properties.getHmacSecret());
            if (!expectedSig.equals(parsed.getHmacSignature())) {
                throw new QrSignatureInvalidException("QR HMAC signature is invalid for tracking code: "
                        + parsed.getTrackingCode());
            }
            log.debug("QR verified successfully for tracking code: {}", parsed.getTrackingCode());
            return parsed;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private QrPayload sign(QrPayload unsigned) {
        String sig = computeHmac(unsigned.signingMessage(), properties.getHmacSecret());
        return unsigned.withSignature(sig);
    }

    private byte[] renderQrPng(QrPayload signedPayload, QRCodeSpec spec) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.valueOf(spec.getErrorCorrectionLevel()));
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(signedPayload.toQRCodeString(), BarcodeFormat.QR_CODE,
                    spec.getSizePx(), spec.getSizePx(), hints);

            int fgColor = Color.decode(spec.getForegroundColor()).getRGB();
            int bgColor = Color.decode(spec.getBackgroundColor()).getRGB();
            MatrixToImageConfig config = new MatrixToImageConfig(fgColor, bgColor);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out, config);
            return out.toByteArray();
        } catch (WriterException | java.io.IOException e) {
            throw new RuntimeException("Failed to render QR code for payload: "
                    + signedPayload.getTrackingCode(), e);
        }
    }

    private String buildQrKey(QrPayload payload) {
        return "qr/" + payload.getMissionId() + "/" + payload.getTrackingCode() + ".png";
    }

    private Mono<Void> persistMetadata(QrPayload payload, String bucket, String key, int size) {
        MediaFile file = MediaFile.create(
                payload.getTenantId(),
                null,
                MediaType.QR_CODE,
                QR_MIME_TYPE,
                payload.getTrackingCode() + ".png",
                bucket,
                key,
                size,
                null,
                false,
                LocalDateTime.now().plusYears(1),
                Map.of("trackingCode", payload.getTrackingCode(),
                       "missionId", payload.getMissionId().toString()));
        return mediaRepository.save(file).then();
    }

    private String computeHmac(String message, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256", e);
        }
    }
}
