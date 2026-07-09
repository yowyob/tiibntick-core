package com.yowyob.tiibntick.core.platformgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration properties for the platform → Core gateway (see
 * {@code docs/auth/platform-client-management-design.md}). Platform clients themselves
 * are entirely DB-backed (no static config list) — see {@code PlatformClientAdminService}
 * / {@code /api/v1/admin/platform-clients}.
 *
 * <p>Example YAML:
 * <pre>
 * tnt:
 *   platform-gateway:
 *     client-cache-ttl: PT45S
 *     sso-app-redirect-urls:
 *       HRM: https://hrm.yowyob.com/sso/callback
 * </pre>
 *
 * @author MANFOUO Braun
 */
@ConfigurationProperties(prefix = "tnt.platform-gateway")
public class TntPlatformGatewayProperties {

    /**
     * {@code app code -> redirect base URL}, used by the optional
     * {@code POST /api/v1/sso/yowyob/launch} composite endpoint.
     */
    private Map<String, String> ssoAppRedirectUrls = Map.of();

    /** Short-TTL in-process cache for the DB-backed client/scope lookup (decided 2026-07-08: TTL-only, no active invalidation broadcast). */
    private Duration clientCacheTtl = Duration.ofSeconds(45);

    public Map<String, String> getSsoAppRedirectUrls() { return ssoAppRedirectUrls; }
    public void setSsoAppRedirectUrls(Map<String, String> ssoAppRedirectUrls) { this.ssoAppRedirectUrls = ssoAppRedirectUrls; }

    public Duration getClientCacheTtl() { return clientCacheTtl; }
    public void setClientCacheTtl(Duration clientCacheTtl) { this.clientCacheTtl = clientCacheTtl; }
}
