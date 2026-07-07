package com.yowyob.tiibntick.core.media.port.inbound;

import com.yowyob.tiibntick.core.media.domain.QRCodeSpec;
import com.yowyob.tiibntick.core.media.domain.QrPayload;
import reactor.core.publisher.Mono;

/**
 * Inbound port — QR code generation use case.
 * <p>
 * Implementations MUST:
 * <ul>
 *   <li>Sign the {@link QrPayload} with the tenant-specific HMAC key before encoding.</li>
 *   <li>Return the raw bytes of the generated image (PNG or SVG).</li>
 *   <li>Store the generated QR file in MinIO via {@link IObjectStorageClient}.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public interface IGenerateQrCodeUseCase {

    /**
     * Generates and stores a QR code image for the given payload.
     *
     * @param payload the logistics payload to encode (will be signed internally)
     * @param spec    rendering specification (size, format, color, etc.)
     * @return MinIO storage key of the persisted QR image
     */
    Mono<String> generate(QrPayload payload, QRCodeSpec spec);

    /**
     * Verifies and decodes a raw QR code string scanned from a physical label.
     *
     * @param rawQrData raw data string extracted from QR scan
     * @param tenantId  tenant context for HMAC key lookup
     * @return the verified and decoded {@link QrPayload}
     * @throws com.yowyob.tiibntick.core.media.domain.exception.QrSignatureInvalidException
     *         if the HMAC signature does not match
     */
    Mono<QrPayload> verify(String rawQrData, String tenantId);
}
