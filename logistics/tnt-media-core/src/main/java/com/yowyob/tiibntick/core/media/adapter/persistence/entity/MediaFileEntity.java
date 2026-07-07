package com.yowyob.tiibntick.core.media.adapter.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * R2DBC persistence entity for {@link com.yowyob.tiibntick.core.media.domain.MediaFile}.
 * Maps to the {@code tnt_media.media_files} table.
 * <p>
 * Note: JSON metadata is stored as a {@code TEXT} column and (de)serialized
 * by the repository adapter using Jackson.
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "tnt_media", value = "media_files")
public class MediaFileEntity {

    @Id
    @Column("id")
    private UUID id;

    @Column("tenant_id")
    private String tenantId;

    @Column("owner_user_id")
    private String ownerUserId;

    @Column("media_type")
    private String mediaType;

    @Column("mime_type")
    private String mimeType;

    @Column("original_file_name")
    private String originalFileName;

    @Column("storage_bucket")
    private String storageBucket;

    @Column("storage_key")
    private String storageKey;

    @Column("size_bytes")
    private Long sizeBytes;

    @Column("sha256_hash")
    private String sha256Hash;

    @Column("is_public")
    private boolean isPublic;

    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Column("uploaded_at")
    private LocalDateTime uploadedAt;

    /** JSON-serialized metadata map stored as TEXT. */
    @Column("metadata_json")
    private String metadataJson;
}
