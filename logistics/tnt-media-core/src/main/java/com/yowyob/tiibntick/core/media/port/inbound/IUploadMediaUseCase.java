package com.yowyob.tiibntick.core.media.port.inbound;

import com.yowyob.tiibntick.core.media.domain.ImageProcessingSpec;
import com.yowyob.tiibntick.core.media.domain.MediaFile;
import com.yowyob.tiibntick.core.media.domain.MediaFileId;
import com.yowyob.tiibntick.core.media.domain.MediaType;
import reactor.core.publisher.Mono;

/**
 * Inbound port — generic media upload use case.
 * <p>
 * Handles file upload lifecycle: validation → optional image processing →
 * SHA-256 hash computation → MinIO upload → metadata persistence.
 *
 * @author MANFOUO Braun
 */
public interface IUploadMediaUseCase {

    /**
     * Uploads a raw file to MinIO and records its metadata in PostgreSQL.
     *
     * @param tenantId         the owning tenant
     * @param ownerUserId      optional actor who owns this file
     * @param type             logical media type
     * @param mimeType         MIME type of the file
     * @param originalFileName original file name
     * @param data             raw file bytes
     * @param isPublic         whether the file should be publicly accessible
     * @return the persisted {@link MediaFile} aggregate
     */
    Mono<MediaFile> upload(
            String tenantId,
            String ownerUserId,
            MediaType type,
            String mimeType,
            String originalFileName,
            byte[] data,
            boolean isPublic);

    /**
     * Uploads an image with optional pre-processing (resize, watermark, grayscale).
     *
     * @param tenantId         the owning tenant
     * @param ownerUserId      optional actor
     * @param type             logical media type (should be an image type)
     * @param originalFileName original file name
     * @param imageData        raw image bytes
     * @param spec             image processing parameters
     * @return the persisted {@link MediaFile} aggregate
     */
    Mono<MediaFile> uploadImage(
            String tenantId,
            String ownerUserId,
            MediaType type,
            String originalFileName,
            byte[] imageData,
            ImageProcessingSpec spec);

    /**
     * Returns a pre-signed URL for the given file, valid for the specified TTL.
     *
     * @param fileId     the file to generate a URL for
     * @param ttlSeconds URL validity in seconds
     * @return pre-signed URL string
     */
    Mono<String> generatePresignedUrl(MediaFileId fileId, int ttlSeconds);

    /**
     * Deletes a file from both MinIO and the metadata store.
     * Idempotent — does not fail if the file does not exist.
     *
     * @param fileId   the file to delete
     * @param tenantId tenant context for authorization
     * @return completion signal
     */
    Mono<Void> delete(MediaFileId fileId, String tenantId);
}
