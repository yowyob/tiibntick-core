package com.yowyob.tiibntick.core.roles.domain.exception;

import java.util.UUID;

/**
 * Root domain exception for TiiBnTick RBAC operations.
 *
 * @author MANFOUO Braun
 */
public class TntRoleException extends RuntimeException {

    private final String code;

    public TntRoleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public TntRoleException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static TntRoleException forbidden(String resource, String action) {
        return new TntRoleException(
                "ROLE_FORBIDDEN",
                "Access denied: permission '" + resource + ":" + action + "' is required."
        );
    }

    public static TntRoleException forbidden(String permission) {
        return new TntRoleException("ROLE_FORBIDDEN", "Access denied: permission '" + permission + "' is required.");
    }

    public static TntRoleException unknownRole(String code) {
        return new TntRoleException("ROLE_UNKNOWN", "Unknown TiiBnTick role: " + code);
    }

    public static TntRoleException roleAlreadyExists(String code, String tenantId) {
        return new TntRoleException(
                "ROLE_ALREADY_EXISTS",
                "Role '" + code + "' already exists for tenant " + tenantId
        );
    }

    public static TntRoleException provisioningFailed(String role, Throwable cause) {
        return new TntRoleException(
                "ROLE_PROVISIONING_FAILED",
                "Failed to provision TiiBnTick role '" + role + "': " + cause.getMessage(),
                cause
        );
    }

    public static TntRoleException missingContext() {
        return new TntRoleException(
                "ROLE_MISSING_CONTEXT",
                "No authenticated security context available for permission evaluation."
        );
    }

    public static TntRoleException roleNotFoundInKernel(String code) {
        return new TntRoleException(
                "ROLE_NOT_PROVISIONED",
                "Role '" + code + "' is not provisioned in the Kernel's system tenant yet."
        );
    }

    public static TntRoleException missingScopeId(String code, String scopeType) {
        return new TntRoleException(
                "ROLE_MISSING_SCOPE_ID",
                "Role '" + code + "' has scope " + scopeType + " and requires a scopeId to be assigned."
        );
    }

    public static TntRoleException assignmentFailed(String code, UUID targetUserId) {
        return new TntRoleException(
                "ROLE_ASSIGNMENT_FAILED",
                "Failed to assign role '" + code + "' to user " + targetUserId + " in the Kernel."
        );
    }

    public static TntRoleException deletionFailed(UUID kernelRoleId, Throwable cause) {
        return new TntRoleException(
                "ROLE_DELETION_FAILED",
                "Failed to delete role " + kernelRoleId + " from the Kernel: " + cause.getMessage(),
                cause
        );
    }

    public static TntRoleException revocationFailed(UUID kernelAssignmentId, Throwable cause) {
        return new TntRoleException(
                "ROLE_REVOCATION_FAILED",
                "Failed to revoke role assignment " + kernelAssignmentId + " in the Kernel: " + cause.getMessage(),
                cause
        );
    }

    /**
     * A tenant-defined custom role's {@code code} collides with one of the 9 canonical
     * {@code TntRole} codes, which are system-owned and not user-creatable.
     */
    public static TntRoleException roleCodeReserved(String code) {
        return new TntRoleException(
                "ROLE_CODE_RESERVED",
                "Role code '" + code + "' is reserved for a canonical TiiBnTick system role and cannot be used for a custom role."
        );
    }

    /**
     * An update/delete was attempted against one of the 9 canonical system roles, which are
     * not editable by tenants (see {@code ManageTntRoleUseCase}).
     */
    public static TntRoleException systemRoleNotEditable(String code) {
        return new TntRoleException(
                "ROLE_SYSTEM_NOT_EDITABLE",
                "Role '" + code + "' is a canonical TiiBnTick system role and cannot be modified or deleted."
        );
    }

    /**
     * No local {@link com.yowyob.tiibntick.core.roles.domain.model.Role} row exists for
     * {@code roleId} under {@code tenantId}.
     */
    public static TntRoleException roleNotFound(UUID tenantId, UUID roleId) {
        return new TntRoleException(
                "ROLE_NOT_FOUND",
                "No role " + roleId + " found for tenant " + tenantId + "."
        );
    }

    /**
     * No local {@link com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment} row
     * exists for {@code assignmentId} under {@code tenantId}.
     */
    public static TntRoleException assignmentNotFound(UUID tenantId, UUID assignmentId) {
        return new TntRoleException(
                "ROLE_ASSIGNMENT_NOT_FOUND",
                "No role assignment " + assignmentId + " found for tenant " + tenantId + "."
        );
    }

    /**
     * A non-SYSTEM-scoped role assignment was requested without a resolvable tenant id —
     * the local {@code UserRoleAssignment} row would otherwise be unfindable by
     * {@code LocalReactivePermissionResolver} at request time.
     */
    public static TntRoleException missingTenantId(String code) {
        return new TntRoleException(
                "ROLE_MISSING_TENANT_ID",
                "Role '" + code + "' requires a tenantId to record the local assignment."
        );
    }

    /**
     * A canonical role code was requested for assignment, but no local {@code Role} row
     * exists yet for it under the system tenant — {@link
     * com.yowyob.tiibntick.core.roles.application.service.TntRoleInitializationService}
     * must seed canonical roles before any assignment can be processed. A genuine
     * startup-ordering invariant, not a case to silently paper over.
     */
    public static TntRoleException roleNotSeeded(String code) {
        return new TntRoleException(
                "ROLE_NOT_SEEDED",
                "Canonical role '" + code + "' has not been seeded locally yet — "
                        + "TntRoleInitializationService must provision it before it can be assigned."
        );
    }

    /**
     * Jackson failed to serialize a {@code RoleSyncOutboxEntry} payload before enqueueing it.
     */
    public static TntRoleException outboxPayloadSerializationFailed(Throwable cause) {
        return new TntRoleException(
                "ROLE_SYNC_PAYLOAD_SERIALIZATION_FAILED",
                "Failed to serialize RBAC outbox payload: " + cause.getMessage(),
                cause
        );
    }
}
