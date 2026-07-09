package com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * R2DBC persistence entity for the {@code tnt_api_keys} table. Mapped to/from
 * {@link com.yowyob.tiibntick.core.platformgateway.domain.model.ApiKey} by
 * {@code PlatformClientPersistenceMapper}.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_api_keys")
public class ApiKeyEntity {

    @Id
    @Column("id")
    private String id;

    @Column("platform_client_id")
    private String platformClientId;

    @Column("key_prefix")
    private String keyPrefix;

    @Column("key_hash")
    private String keyHash;

    @Column("status")
    private String status;

    @Column("expires_at")
    private Instant expiresAt;

    @Column("last_used_at")
    private Instant lastUsedAt;

    @Column("created_at")
    private Instant createdAt;

    @Column("revoked_at")
    private Instant revokedAt;

    @Column("revoked_by")
    private String revokedBy;

    @Column("revoked_reason")
    private String revokedReason;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPlatformClientId() { return platformClientId; }
    public void setPlatformClientId(String platformClientId) { this.platformClientId = platformClientId; }

    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }

    public String getRevokedReason() { return revokedReason; }
    public void setRevokedReason(String revokedReason) { this.revokedReason = revokedReason; }
}
