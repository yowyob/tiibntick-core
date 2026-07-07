package com.yowyob.tiibntick.core.media.port.outbound;

import com.yowyob.tiibntick.core.media.domain.MediaFile;
import com.yowyob.tiibntick.core.media.domain.MediaFileId;
import com.yowyob.tiibntick.core.media.domain.MediaType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Outbound port — persistence contract for {@link MediaFile} metadata.
 * Implemented by the R2DBC adapter in the infrastructure layer.
 *
 * @author MANFOUO Braun
 */
public interface IMediaRepository {

    /**
     * Persists a new {@link MediaFile} or replaces an existing one.
     *
     * @param mediaFile the aggregate to save
     * @return saved aggregate with database-assigned fields
     */
    Mono<MediaFile> save(MediaFile mediaFile);

    /**
     * Finds a media file by its primary key.
     *
     * @param id the file identifier
     * @return the aggregate, or empty if not found
     */
    Mono<MediaFile> findById(MediaFileId id);

    /**
     * Finds all media files belonging to a specific tenant.
     *
     * @param tenantId tenant filter
     * @return flux of matching files
     */
    Flux<MediaFile> findByTenantId(String tenantId);

    /**
     * Finds all media files of a specific type for a tenant.
     *
     * @param tenantId  tenant filter
     * @param mediaType type filter
     * @return flux of matching files
     */
    Flux<MediaFile> findByTenantIdAndType(String tenantId, MediaType mediaType);

    /**
     * Finds all expired files (candidates for cleanup).
     *
     * @param now reference timestamp
     * @return flux of expired files
     */
    Flux<MediaFile> findExpiredBefore(LocalDateTime now);

    /**
     * Deletes a file record by ID. Does not remove the object from MinIO.
     *
     * @param id file identifier
     * @return completion signal
     */
    Mono<Void> deleteById(MediaFileId id);

    /**
     * Checks whether a file with the given storage key already exists
     * (deduplication via SHA-256 hash).
     *
     * @param sha256Hash hash to check
     * @param tenantId   tenant scope
     * @return existing file if found, or empty
     */
    Mono<MediaFile> findByHashAndTenant(String sha256Hash, String tenantId);
}
