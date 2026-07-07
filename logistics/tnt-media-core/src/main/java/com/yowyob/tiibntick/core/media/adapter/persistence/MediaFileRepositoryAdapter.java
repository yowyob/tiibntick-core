package com.yowyob.tiibntick.core.media.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.media.adapter.persistence.entity.MediaFileEntity;
import com.yowyob.tiibntick.core.media.domain.MediaFile;
import com.yowyob.tiibntick.core.media.domain.MediaFileId;
import com.yowyob.tiibntick.core.media.domain.MediaType;
import com.yowyob.tiibntick.core.media.port.outbound.IMediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

/**
 * Infrastructure adapter implementing {@link IMediaRepository} via R2DBC.
 * Handles all mapping between the {@link MediaFile} domain aggregate and
 * the {@link MediaFileEntity} persistence entity.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaFileRepositoryAdapter implements IMediaRepository {

    private final MediaFileR2dbcRepository r2dbcRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<MediaFile> save(MediaFile mediaFile) {
        MediaFileEntity entity = toEntity(mediaFile);
        return r2dbcRepository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<MediaFile> findById(MediaFileId id) {
        return r2dbcRepository.findById(id.getValue()).map(this::toDomain);
    }

    @Override
    public Flux<MediaFile> findByTenantId(String tenantId) {
        return r2dbcRepository.findByTenantId(tenantId).map(this::toDomain);
    }

    @Override
    public Flux<MediaFile> findByTenantIdAndType(String tenantId, MediaType mediaType) {
        return r2dbcRepository.findByTenantIdAndMediaType(tenantId, mediaType.name())
                .map(this::toDomain);
    }

    @Override
    public Flux<MediaFile> findExpiredBefore(LocalDateTime now) {
        return r2dbcRepository.findExpiredBefore(now).map(this::toDomain);
    }

    @Override
    public Mono<Void> deleteById(MediaFileId id) {
        return r2dbcRepository.deleteById(id.getValue());
    }

    @Override
    public Mono<MediaFile> findByHashAndTenant(String sha256Hash, String tenantId) {
        return r2dbcRepository.findByHashAndTenant(sha256Hash, tenantId).map(this::toDomain);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private MediaFileEntity toEntity(MediaFile domain) {
        return MediaFileEntity.builder()
                .id(domain.getId().getValue())
                .tenantId(domain.getTenantId())
                .ownerUserId(domain.getOwnerUserId())
                .mediaType(domain.getType().name())
                .mimeType(domain.getMimeType())
                .originalFileName(domain.getOriginalFileName())
                .storageBucket(domain.getStorageBucket())
                .storageKey(domain.getStorageKey())
                .sizeBytes(domain.getSizeBytes())
                .sha256Hash(domain.getSha256Hash())
                .isPublic(domain.isPublic())
                .expiresAt(domain.getExpiresAt())
                .uploadedAt(domain.getUploadedAt() != null ? domain.getUploadedAt() : LocalDateTime.now())
                .metadataJson(serializeMetadata(domain.getMetadata()))
                .build();
    }

    private MediaFile toDomain(MediaFileEntity entity) {
        return MediaFile.builder()
                .id(MediaFileId.of(entity.getId()))
                .tenantId(entity.getTenantId())
                .ownerUserId(entity.getOwnerUserId())
                .type(MediaType.valueOf(entity.getMediaType()))
                .mimeType(entity.getMimeType())
                .originalFileName(entity.getOriginalFileName())
                .storageBucket(entity.getStorageBucket())
                .storageKey(entity.getStorageKey())
                .sizeBytes(entity.getSizeBytes())
                .sha256Hash(entity.getSha256Hash())
                .isPublic(entity.isPublic())
                .expiresAt(entity.getExpiresAt())
                .uploadedAt(entity.getUploadedAt())
                .metadata(deserializeMetadata(entity.getMetadataJson()))
                .build();
    }

    private String serializeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize metadata", e);
            return "{}";
        }
    }

    private Map<String, String> deserializeMetadata(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize metadata JSON: {}", json, e);
            return Collections.emptyMap();
        }
    }
}
