package com.yowyob.tiibntick.core.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for tnt-auth-core.
 * Bound from the {@code tnt.auth} prefix in application.yml.
 *
 * <p>Example YAML:
 * <pre>
 * tnt:
 *   auth:
 *     service-code: TNT_AGENCY
 *     token-cache-ttl: PT14M
 *     actor-resolution-enabled: true
 * </pre>
 *
 * @author MANFOUO Braun
 */
@ConfigurationProperties(prefix = "tnt.auth")
public class TntAuthProperties {

    /**
     * TiiBnTick platform service code passed to the Kernel's token exchange.
     * Identifies which service is requesting access (e.g. TNT_AGENCY, TNT_LINK).
     */
    private String serviceCode = "TNT_CORE";

    /**
     * Duration to cache the resolved TntSecurityContext in Reactor context.
     * Should be less than the token's own TTL (access_token expires_in).
     */
    private Duration tokenCacheTtl = Duration.ofMinutes(14);

    /**
     * When true, enriches the security context with actor data via IYowAuthTntAdapter.
     * Disable in tests or environments without tnt-actor-core.
     */
    private boolean actorResolutionEnabled = true;

    /**
     * When true, endpoints that do not have a security context return an anonymous
     * TntSecurityContext rather than throwing an error.
     * Set to false for strictly secured APIs.
     */
    private boolean allowAnonymousContext = false;

    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }

    public Duration getTokenCacheTtl() { return tokenCacheTtl; }
    public void setTokenCacheTtl(Duration tokenCacheTtl) { this.tokenCacheTtl = tokenCacheTtl; }

    public boolean isActorResolutionEnabled() { return actorResolutionEnabled; }
    public void setActorResolutionEnabled(boolean actorResolutionEnabled) {
        this.actorResolutionEnabled = actorResolutionEnabled;
    }

    public boolean isAllowAnonymousContext() { return allowAnonymousContext; }
    public void setAllowAnonymousContext(boolean allowAnonymousContext) {
        this.allowAnonymousContext = allowAnonymousContext;
    }
}
