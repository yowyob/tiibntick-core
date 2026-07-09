package com.yowyob.tiibntick.core.platformgateway.domain.exception;

/**
 * Root domain exception for the platform gateway: platform-client authentication,
 * scope authorization, SSO orchestration, and admin CRUD errors. Never wraps Spring
 * Security exceptions directly — maintains the hexagonal boundary.
 *
 * @author MANFOUO Braun
 */
public class TntPlatformGatewayException extends RuntimeException {

    private final String code;

    public TntPlatformGatewayException(String code, String message) {
        super(message);
        this.code = code;
    }

    public TntPlatformGatewayException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static TntPlatformGatewayException unauthorized(String message) {
        return new TntPlatformGatewayException("PLATFORM_UNAUTHORIZED", message);
    }

    /**
     * A platform client authenticated successfully but lacks the scope required for
     * the requested resource/action (see {@code PermissionMatcher}, {@code resource:action}
     * scope format).
     */
    public static TntPlatformGatewayException scopeForbidden(String resource, String action) {
        return new TntPlatformGatewayException(
                "PLATFORM_SCOPE_FORBIDDEN",
                "This platform client is not granted scope '" + resource + ":" + action + "'.");
    }

    public static TntPlatformGatewayException clientNotFound(String clientId) {
        return new TntPlatformGatewayException("PLATFORM_CLIENT_NOT_FOUND", "No platform client found for clientId=" + clientId);
    }

    public static TntPlatformGatewayException apiKeyNotFound(String keyId) {
        return new TntPlatformGatewayException("API_KEY_NOT_FOUND", "No API key found for id=" + keyId);
    }

    public static TntPlatformGatewayException invalidScope(String scope) {
        return new TntPlatformGatewayException("PLATFORM_INVALID_SCOPE", "Unknown scope resource in '" + scope + "'");
    }

    public static TntPlatformGatewayException ssoContextNotFound(String kernelOrganizationId) {
        return new TntPlatformGatewayException(
                "SSO_CONTEXT_NOT_FOUND",
                "No SSO context found matching kernelOrganizationId=" + kernelOrganizationId
        );
    }

    public static TntPlatformGatewayException ssoTokenExchangeFailed(String detail) {
        return new TntPlatformGatewayException("SSO_TOKEN_EXCHANGE_FAILED", "SSO token exchange failed: " + detail);
    }

    public static TntPlatformGatewayException ssoAppNotConfigured(String app) {
        return new TntPlatformGatewayException("SSO_APP_NOT_CONFIGURED",
                "No redirect URL configured for SSO app '" + app + "' (tnt.platform-gateway.sso-app-redirect-urls)");
    }
}
