package com.yowyob.tiibntick.core.media.port.outbound;

import reactor.core.publisher.Mono;

import java.io.InputStream;

/**
 * Outbound port — object storage abstraction.
 * Implemented by {@link com.yowyob.tiibntick.core.media.adapter.storage.MinioStorageClient}.
 * <p>
 * All operations are reactive (Project Reactor) to integrate smoothly with WebFlux.
 *
 * @author MANFOUO Braun
 */
public interface IObjectStorageClient {

    /**
     * Ensures the bucket exists for the given tenant. Creates it if missing.
     * Idempotent — safe to call on every upload.
     *
     * @param bucketName bucket name following the {@code tnt-{tenantId}} convention
     * @return completion signal
     */
    Mono<Void> ensureBucketExists(String bucketName);

    /**
     * Uploads a byte array to object storage.
     *
     * @param bucketName  destination bucket
     * @param objectKey   object key / path within the bucket
     * @param data        content to upload
     * @param contentType MIME type
     * @return ETag of the uploaded object (useful for integrity checks)
     */
    Mono<String> upload(String bucketName, String objectKey, byte[] data, String contentType);

    /**
     * Uploads a stream to object storage (suitable for large files).
     *
     * @param bucketName    destination bucket
     * @param objectKey     object key
     * @param inputStream   input stream
     * @param contentLength total content length in bytes (pass -1 if unknown)
     * @param contentType   MIME type
     * @return ETag of the uploaded object
     */
    Mono<String> uploadStream(
            String bucketName,
            String objectKey,
            InputStream inputStream,
            long contentLength,
            String contentType);

    /**
     * Downloads an object as raw bytes.
     *
     * @param bucketName bucket containing the object
     * @param objectKey  object key
     * @return raw content bytes
     */
    Mono<byte[]> download(String bucketName, String objectKey);

    /**
     * Generates a pre-signed URL granting temporary read access to a private object.
     *
     * @param bucketName bucket containing the object
     * @param objectKey  object key
     * @param ttlSeconds URL validity in seconds
     * @return pre-signed URL string
     */
    Mono<String> presignedGetUrl(String bucketName, String objectKey, int ttlSeconds);

    /**
     * Deletes an object from storage. Idempotent — does not fail if the object
     * does not exist.
     *
     * @param bucketName bucket containing the object
     * @param objectKey  object key
     * @return completion signal
     */
    Mono<Void> delete(String bucketName, String objectKey);

    /**
     * Checks whether an object exists in storage.
     *
     * @param bucketName bucket
     * @param objectKey  object key
     * @return {@code true} if the object exists
     */
    Mono<Boolean> exists(String bucketName, String objectKey);
}
