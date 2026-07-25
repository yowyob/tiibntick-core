package com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * R2DBC persistence entity for the {@code tnt_api_key_rotation_history} table.
 * Mapped to/from
 * {@link com.yowyob.tiibntick.core.platformgateway.domain.model.ApiKeyRotationRecord}
 * by {@code PlatformClientPersistenceMapper}.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_api_key_rotation_history")
public class ApiKeyRotationHistoryEntity implements Persistable<String>, TntPersistableEntity {

    @Id
    @Column("id")
    private String id;

    @Transient
    private boolean isNew = true;

    @Column("platform_client_id")
    private String platformClientId;

    @Column("old_api_key_id")
    private String oldApiKeyId;

    @Column("new_api_key_id")
    private String newApiKeyId;

    @Column("rotated_at")
    private Instant rotatedAt;

    @Column("rotated_by")
    private String rotatedBy;

    @Column("reason")
    private String reason;

    @Override
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }

    public String getPlatformClientId() { return platformClientId; }
    public void setPlatformClientId(String platformClientId) { this.platformClientId = platformClientId; }

    public String getOldApiKeyId() { return oldApiKeyId; }
    public void setOldApiKeyId(String oldApiKeyId) { this.oldApiKeyId = oldApiKeyId; }

    public String getNewApiKeyId() { return newApiKeyId; }
    public void setNewApiKeyId(String newApiKeyId) { this.newApiKeyId = newApiKeyId; }

    public Instant getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(Instant rotatedAt) { this.rotatedAt = rotatedAt; }

    public String getRotatedBy() { return rotatedBy; }
    public void setRotatedBy(String rotatedBy) { this.rotatedBy = rotatedBy; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
