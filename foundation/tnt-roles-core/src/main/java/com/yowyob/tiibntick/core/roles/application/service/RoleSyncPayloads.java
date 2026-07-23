package com.yowyob.tiibntick.core.roles.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;

import java.util.Set;
import java.util.UUID;

/**
 * Wire-format payload shapes serialized into {@code RoleSyncOutboxEntry#payload} by this
 * package's application services, plus the shared Jackson serialization helper.
 *
 * <p>Package-private — these are an internal outbox contract between the write-side
 * services in this package and the (separately built) Kernel sync worker that will
 * eventually deserialize and replay them; they are not part of any public port.
 *
 * @author MANFOUO Braun
 */
final class RoleSyncPayloads {

    private RoleSyncPayloads() {
    }

    /** Payload for {@code RoleSyncOperation.ASSIGN_ROLE} entries. */
    record AssignRolePayload(UUID userId, String roleCode, String scopeType, UUID scopeId) {
    }

    /** Payload for {@code RoleSyncOperation.PROVISION_ROLE} entries. */
    record ProvisionRolePayload(UUID tenantId, String code, String name, String scopeType, Set<String> permissions) {
    }

    /** Payload for {@code RoleSyncOperation.DELETE_ROLE} entries. */
    record DeleteRolePayload(UUID tenantId, UUID kernelRoleId) {
    }

    /** Payload for {@code RoleSyncOperation.REVOKE_ASSIGNMENT} entries. */
    record RevokeAssignmentPayload(UUID kernelAssignmentId) {
    }

    /**
     * Serializes {@code payload} to JSON, wrapping any {@link JsonProcessingException} in a
     * {@link TntRoleException} so callers can propagate it as a reactive error signal.
     */
    static String toJson(ObjectMapper objectMapper, Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw TntRoleException.outboxPayloadSerializationFailed(e);
        }
    }
}
