package com.yowyob.tiibntick.core.media.adapter.in.rest;

import com.yowyob.tiibntick.core.media.domain.MediaFileId;
import com.yowyob.tiibntick.core.media.domain.MediaType;
import com.yowyob.tiibntick.core.media.port.inbound.IUploadMediaUseCase;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST adapter consumed by tnt-agency {@code MediaCoreClient}.
 */
@RestController
@RequestMapping("/api/v1/media")
public class MediaRestController {

    private static final int DEFAULT_TTL_SECONDS = 3600;

    private final IUploadMediaUseCase uploadUseCase;

    public MediaRestController(IUploadMediaUseCase uploadUseCase) {
        this.uploadUseCase = uploadUseCase;
    }

    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<UploadResponse> upload(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam("mediaType") String mediaType,
            @RequestParam(value = "category", required = false) String category,
            @RequestPart("file") FilePart file) {
        MediaType type = resolveMediaType(mediaType, category);
        String mimeType = file.headers().getContentType() != null
                ? file.headers().getContentType().toString()
                : org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return DataBufferUtils.join(file.content())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .flatMap(data -> uploadUseCase.upload(
                                tenantId,
                                userId,
                                type,
                                mimeType,
                                file.filename(),
                                data,
                                false)
                        .flatMap(media -> uploadUseCase.generatePresignedUrl(media.getId(), DEFAULT_TTL_SECONDS)
                                .map(url -> new UploadResponse(
                                        media.getId().getValue(),
                                        media.getStorageKey(),
                                        mimeType,
                                        media.getSizeBytes(),
                                        url))));
    }

    @GetMapping("/{mediaId}/download-url")
    public Mono<DownloadUrlResponse> downloadUrl(
            @PathVariable UUID mediaId,
            @RequestParam(defaultValue = "3600") int ttlSeconds) {
        return uploadUseCase.generatePresignedUrl(MediaFileId.of(mediaId), ttlSeconds)
                .map(url -> new DownloadUrlResponse(url, ttlSeconds));
    }

    private static MediaType resolveMediaType(String mediaType, String category) {
        if (mediaType == null || mediaType.isBlank()) {
            return MediaType.KYC_DOCUMENT;
        }
        try {
            return MediaType.valueOf(mediaType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            if ("proof".equalsIgnoreCase(category) || "delivery".equalsIgnoreCase(category)) {
                return MediaType.DELIVERY_PROOF_PHOTO;
            }
            return MediaType.KYC_DOCUMENT;
        }
    }

    public record UploadResponse(
            UUID mediaId,
            String storageKey,
            String mimeType,
            long sizeBytes,
            String downloadUrl) {}

    public record DownloadUrlResponse(String url, int ttlSeconds) {}
}
