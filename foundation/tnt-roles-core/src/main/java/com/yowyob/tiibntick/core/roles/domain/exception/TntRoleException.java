package com.yowyob.tiibntick.core.roles.domain.exception;

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
}
