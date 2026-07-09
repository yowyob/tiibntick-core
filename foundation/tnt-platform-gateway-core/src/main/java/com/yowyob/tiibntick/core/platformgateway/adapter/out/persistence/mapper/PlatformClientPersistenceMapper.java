package com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.mapper;

import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.entity.ApiKeyEntity;
import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.entity.ApiKeyRotationHistoryEntity;
import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.entity.ClientAuditLogEntity;
import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.entity.ClientPermissionEntity;
import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.entity.PlatformClientEntity;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ApiKey;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ApiKeyRotationRecord;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ApiKeyStatus;
import com.yowyob.tiibntick.core.platformgateway.domain.model.AuditOutcome;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientAuditLog;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientPermission;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientStatus;
import com.yowyob.tiibntick.core.platformgateway.domain.model.Environment;
import com.yowyob.tiibntick.core.platformgateway.domain.model.PlatformClient;

import java.util.UUID;

/**
 * Hand-written entity/domain mapper for the platform-gateway persistence layer — plain
 * field mapping, no MapStruct needed given the small, flat shape of these aggregates.
 *
 * @author MANFOUO Braun
 */
public final class PlatformClientPersistenceMapper {

    private PlatformClientPersistenceMapper() {
    }

    // ── PlatformClient ───────────────────────────────────────────────────────

    public static PlatformClient toDomain(PlatformClientEntity e) {
        if (e == null) return null;
        return new PlatformClient(
                UUID.fromString(e.getId()),
                e.getClientId(),
                e.getName(),
                e.getPlatformCode(),
                Environment.valueOf(e.getEnvironment()),
                ClientStatus.valueOf(e.getStatus()),
                e.getDescription(),
                e.getContactEmail(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getCreatedBy(),
                e.getUpdatedBy());
    }

    public static PlatformClientEntity toEntity(PlatformClient d) {
        PlatformClientEntity e = new PlatformClientEntity();
        e.setId(d.id().toString());
        e.setClientId(d.clientId());
        e.setName(d.name());
        e.setPlatformCode(d.platformCode());
        e.setEnvironment(d.environment().name());
        e.setStatus(d.status().name());
        e.setDescription(d.description());
        e.setContactEmail(d.contactEmail());
        e.setCreatedAt(d.createdAt());
        e.setUpdatedAt(d.updatedAt());
        e.setCreatedBy(d.createdBy());
        e.setUpdatedBy(d.updatedBy());
        return e;
    }

    // ── ApiKey ────────────────────────────────────────────────────────────────

    public static ApiKey toDomain(ApiKeyEntity e) {
        if (e == null) return null;
        return new ApiKey(
                UUID.fromString(e.getId()),
                UUID.fromString(e.getPlatformClientId()),
                e.getKeyPrefix(),
                e.getKeyHash(),
                ApiKeyStatus.valueOf(e.getStatus()),
                e.getExpiresAt(),
                e.getLastUsedAt(),
                e.getCreatedAt(),
                e.getRevokedAt(),
                e.getRevokedBy(),
                e.getRevokedReason());
    }

    public static ApiKeyEntity toEntity(ApiKey d) {
        ApiKeyEntity e = new ApiKeyEntity();
        e.setId(d.id().toString());
        e.setPlatformClientId(d.platformClientId().toString());
        e.setKeyPrefix(d.keyPrefix());
        e.setKeyHash(d.keyHash());
        e.setStatus(d.status().name());
        e.setExpiresAt(d.expiresAt());
        e.setLastUsedAt(d.lastUsedAt());
        e.setCreatedAt(d.createdAt());
        e.setRevokedAt(d.revokedAt());
        e.setRevokedBy(d.revokedBy());
        e.setRevokedReason(d.revokedReason());
        return e;
    }

    // ── ClientPermission ──────────────────────────────────────────────────────

    public static ClientPermission toDomain(ClientPermissionEntity e) {
        if (e == null) return null;
        return new ClientPermission(
                UUID.fromString(e.getId()),
                UUID.fromString(e.getPlatformClientId()),
                e.getScope(),
                e.getGrantedAt(),
                e.getGrantedBy());
    }

    public static ClientPermissionEntity toEntity(ClientPermission d) {
        ClientPermissionEntity e = new ClientPermissionEntity();
        e.setId(d.id().toString());
        e.setPlatformClientId(d.platformClientId().toString());
        e.setScope(d.scope());
        e.setGrantedAt(d.grantedAt());
        e.setGrantedBy(d.grantedBy());
        return e;
    }

    // ── ApiKeyRotationRecord ──────────────────────────────────────────────────

    public static ApiKeyRotationRecord toDomain(ApiKeyRotationHistoryEntity e) {
        if (e == null) return null;
        return new ApiKeyRotationRecord(
                UUID.fromString(e.getId()),
                UUID.fromString(e.getPlatformClientId()),
                e.getOldApiKeyId() != null ? UUID.fromString(e.getOldApiKeyId()) : null,
                UUID.fromString(e.getNewApiKeyId()),
                e.getRotatedAt(),
                e.getRotatedBy(),
                e.getReason());
    }

    public static ApiKeyRotationHistoryEntity toEntity(ApiKeyRotationRecord d) {
        ApiKeyRotationHistoryEntity e = new ApiKeyRotationHistoryEntity();
        e.setId(d.id().toString());
        e.setPlatformClientId(d.platformClientId().toString());
        e.setOldApiKeyId(d.oldApiKeyId() != null ? d.oldApiKeyId().toString() : null);
        e.setNewApiKeyId(d.newApiKeyId().toString());
        e.setRotatedAt(d.rotatedAt());
        e.setRotatedBy(d.rotatedBy());
        e.setReason(d.reason());
        return e;
    }

    // ── ClientAuditLog ────────────────────────────────────────────────────────

    public static ClientAuditLog toDomain(ClientAuditLogEntity e) {
        if (e == null) return null;
        return new ClientAuditLog(
                UUID.fromString(e.getId()),
                e.getPlatformClientId() != null ? UUID.fromString(e.getPlatformClientId()) : null,
                e.getClientIdAttempted(),
                e.getEndpoint(),
                e.getHttpMethod(),
                AuditOutcome.valueOf(e.getOutcome()),
                e.getIpAddress(),
                e.getUserAgent(),
                e.getOccurredAt());
    }

    public static ClientAuditLogEntity toEntity(ClientAuditLog d) {
        ClientAuditLogEntity e = new ClientAuditLogEntity();
        e.setId(d.id().toString());
        e.setPlatformClientId(d.platformClientId() != null ? d.platformClientId().toString() : null);
        e.setClientIdAttempted(d.clientIdAttempted());
        e.setEndpoint(d.endpoint());
        e.setHttpMethod(d.httpMethod());
        e.setOutcome(d.outcome().name());
        e.setIpAddress(d.ipAddress());
        e.setUserAgent(d.userAgent());
        e.setOccurredAt(d.occurredAt());
        return e;
    }
}
