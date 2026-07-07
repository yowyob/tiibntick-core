package com.yowyob.tiibntick.core.media.port.inbound;

import com.yowyob.tiibntick.core.media.domain.MediaFileId;
import com.yowyob.tiibntick.core.media.domain.SignatureCapture;
import reactor.core.publisher.Mono;

/**
 * Inbound port — digital signature capture use case.
 * <p>
 * Handles the capture of recipient signatures on package delivery or hub deposit.
 * The signature image (Base64 PNG or SVG) is validated, decoded, and stored in MinIO.
 *
 * @author MANFOUO Braun
 */
public interface ICaptureSignatureUseCase {

    /**
     * Validates, stores and registers a digital signature drawn by a recipient.
     *
     * @param tenantId       the owning tenant
     * @param missionId      UUID string of the mission this signature belongs to
     * @param recipientName  full name of the recipient who signed
     * @param capture        signature capture data (Base64 image + metadata)
     * @return the {@link MediaFileId} of the stored signature file
     */
    Mono<MediaFileId> captureDeliverySignature(
            String tenantId,
            String missionId,
            String recipientName,
            SignatureCapture capture);

    /**
     * Retrieves the storage key for a signature file by its ID.
     *
     * @param fileId   signature file identifier
     * @param tenantId tenant context for authorization
     * @return MinIO storage key
     */
    Mono<String> getSignatureKey(MediaFileId fileId, String tenantId);
}
