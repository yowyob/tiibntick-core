package com.yowyob.tiibntick.core.media.adapter.storage;

import com.yowyob.tiibntick.core.media.domain.exception.StorageException;
import com.yowyob.tiibntick.core.media.port.outbound.IObjectStorageClient;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * MinIO implementation of {@link IObjectStorageClient}.
 * <p>
 * All MinIO SDK calls are blocking and are offloaded to the bounded elastic scheduler
 * to keep the WebFlux event loop free.
 * <p>
 * Bucket naming convention: {@code tnt-{tenantId}} (created on demand, idempotent).
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioStorageClient implements IObjectStorageClient {

    private final MinioClient minioClient;

    @Override
    public Mono<Void> ensureBucketExists(String bucketName) {
        return Mono.fromCallable(() -> {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build());
            if (!exists) {
                log.info("Creating MinIO bucket: {}", bucketName);
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build());
            }
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorMap(MinioException.class, e -> new StorageException("Failed to ensure bucket exists: " + bucketName, e))
        .then();
    }

    @Override
    public Mono<String> upload(String bucketName, String objectKey, byte[] data, String contentType) {
        return Mono.fromCallable(() -> {
            try (ByteArrayInputStream stream = new ByteArrayInputStream(data)) {
                var response = minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .stream(stream, data.length, -1)
                        .contentType(contentType)
                        .build());
                log.debug("Uploaded to MinIO: {}/{} ({} bytes)", bucketName, objectKey, data.length);
                return response.etag();
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorMap(e -> !(e instanceof StorageException),
                e -> new StorageException("Upload failed: " + bucketName + "/" + objectKey, e));
    }

    @Override
    public Mono<String> uploadStream(
            String bucketName,
            String objectKey,
            InputStream inputStream,
            long contentLength,
            String contentType) {

        return Mono.fromCallable(() -> {
            var response = minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(inputStream, contentLength, -1)
                    .contentType(contentType)
                    .build());
            return response.etag();
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorMap(e -> !(e instanceof StorageException),
                e -> new StorageException("Stream upload failed: " + bucketName + "/" + objectKey, e));
    }

    @Override
    public Mono<byte[]> download(String bucketName, String objectKey) {
        return Mono.fromCallable(() -> {
            try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build())) {
                return stream.readAllBytes();
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorMap(e -> !(e instanceof StorageException),
                e -> new StorageException("Download failed: " + bucketName + "/" + objectKey, e));
    }

    @Override
    public Mono<String> presignedGetUrl(String bucketName, String objectKey, int ttlSeconds) {
        return Mono.fromCallable(() ->
            minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectKey)
                    .expiry(ttlSeconds, TimeUnit.SECONDS)
                    .build())
        )
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorMap(e -> !(e instanceof StorageException),
                e -> new StorageException("Presign failed: " + bucketName + "/" + objectKey, e));
    }

    @Override
    public Mono<Void> delete(String bucketName, String objectKey) {
        return Mono.fromCallable(() -> {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build());
            log.debug("Deleted from MinIO: {}/{}", bucketName, objectKey);
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorMap(e -> !(e instanceof StorageException),
                e -> new StorageException("Delete failed: " + bucketName + "/" + objectKey, e))
        .then();
    }

    @Override
    public Mono<Boolean> exists(String bucketName, String objectKey) {
        return Mono.fromCallable(() -> {
            try {
                minioClient.statObject(StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .build());
                return true;
            } catch (ErrorResponseException e) {
                if ("NoSuchKey".equals(e.errorResponse().code())) {
                    return false;
                }
                throw new StorageException("StatObject failed: " + bucketName + "/" + objectKey, e);
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorMap(e -> !(e instanceof StorageException),
                e -> new StorageException("Exists check failed: " + bucketName + "/" + objectKey, e));
    }
}
