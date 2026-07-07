package com.yowyob.tiibntick.core.media.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregate root representing a stored media file or document within TiiBnTick.
 * <p>
 * Supports multi-tenant isolation via {@code tenantId}: each tenant's files are
 * stored in a dedicated MinIO bucket ({@code tnt-{tenantId}}).
 * <p>
 * Immutable once created — state transitions are explicit domain methods.
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder(toBuilder = true)
public class MediaFile {

    private final MediaFileId id;

    /** Tenant owning this file — determines the MinIO bucket. */
    private final String tenantId;

    /** Optional reference to the user or actor who uploaded this file. */
    private final String ownerUserId;

    /** Logical type of this media — drives access control and retention. */
    private final MediaType type;

    /** MIME type string (e.g., {@code image/png}, {@code application/pdf}). */
    private final String mimeType;

    /** Original file name as provided by the uploader. */
    private final String originalFileName;

    /** MinIO bucket name where the file is stored. */
    private final String storageBucket;

    /** MinIO object key (path within the bucket). */
    private final String storageKey;

    /** File size in bytes. */
    private final Long sizeBytes;

    /** SHA-256 hash of the file content for integrity verification. */
    private final String sha256Hash;

    /** Whether this file is publicly readable without a signed URL. */
    private final boolean isPublic;

    /**
     * Optional expiration timestamp. After this point the file may be
     * purged by the cleanup scheduler.
     */
    private final LocalDateTime expiresAt;

    private final LocalDateTime uploadedAt;

    /** Arbitrary metadata key-value pairs (e.g., missionId, packageId). */
    private final Map<String, String> metadata;

    // ── Domain invariants ─────────────────────────────────────────────────────

    public static MediaFile create(
            String tenantId,
            String ownerUserId,
            MediaType type,
            String mimeType,
            String originalFileName,
            String storageBucket,
            String storageKey,
            long sizeBytes,
            String sha256Hash,
            boolean isPublic,
            LocalDateTime expiresAt,
            Map<String, String> metadata) {

        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(type, "MediaType must not be null");
        Objects.requireNonNull(storageKey, "storageKey must not be null");

        return MediaFile.builder()
                .id(MediaFileId.generate())
                .tenantId(tenantId)
                .ownerUserId(ownerUserId)
                .type(type)
                .mimeType(mimeType)
                .originalFileName(originalFileName)
                .storageBucket(storageBucket)
                .storageKey(storageKey)
                .sizeBytes(sizeBytes)
                .sha256Hash(sha256Hash)
                .isPublic(isPublic)
                .expiresAt(expiresAt)
                .uploadedAt(LocalDateTime.now())
                .metadata(metadata != null ? Map.copyOf(metadata) : Collections.emptyMap())
                .build();
    }

    /**
     * Returns the canonical public URL for this file when {@code isPublic == true}.
     * Callers should not construct this URL themselves; always use this method.
     *
     * @param minioPublicEndpoint the MinIO public endpoint base URL
     * @return public URL string
     * @throws IllegalStateException if the file is not public
     */
    public String publicUrl(String minioPublicEndpoint) {
        if (!isPublic) {
            throw new IllegalStateException(
                "File " + id + " is not public. Use a presigned URL instead.");
        }
        return minioPublicEndpoint.stripTrailing() + "/" + storageBucket + "/" + storageKey;
    }

    /**
     * Checks whether this file has expired relative to the given timestamp.
     *
     * @param now the reference timestamp
     * @return {@code true} if the file has a non-null expiry and the expiry is before now
     */
    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    /**
     * Derives the canonical bucket name for a given tenant following the
     * TiiBnTick naming convention: {@code tnt-{tenantId}}.
     *
     * @param tenantId the tenant identifier
     * @return bucket name
     */
    public static String bucketNameFor(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return "tnt-" + tenantId.toLowerCase();
    }
}
