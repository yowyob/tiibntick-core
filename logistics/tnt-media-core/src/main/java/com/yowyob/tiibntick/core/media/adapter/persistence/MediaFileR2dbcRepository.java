package com.yowyob.tiibntick.core.media.adapter.persistence;

import com.yowyob.tiibntick.core.media.adapter.persistence.entity.MediaFileEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@link MediaFileEntity}.
 * Queries target the {@code tnt_media.media_files} table.
 *
 * @author MANFOUO Braun
 */
public interface MediaFileR2dbcRepository extends ReactiveCrudRepository<MediaFileEntity, UUID> {

    Flux<MediaFileEntity> findByTenantId(String tenantId);

    Flux<MediaFileEntity> findByTenantIdAndMediaType(String tenantId, String mediaType);

    @Query("SELECT * FROM tnt_media.media_files WHERE expires_at IS NOT NULL AND expires_at < :now")
    Flux<MediaFileEntity> findExpiredBefore(LocalDateTime now);

    @Query("SELECT * FROM tnt_media.media_files WHERE sha256_hash = :hash AND tenant_id = :tenantId LIMIT 1")
    Mono<MediaFileEntity> findByHashAndTenant(String hash, String tenantId);
}
