package com.yowyob.tiibntick.core.roles.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

/**
 * Configuration properties for tnt-roles-core.
 * Bound from the {@code tnt.roles} prefix in application.yml.
 *
 * <p>Example YAML:
 * <pre>
 * tnt:
 *   roles:
 *     system-tenant-id: 00000000-0000-0000-0000-000000000001
 *     provision-on-startup: true
 *     permission-cache-ttl-seconds: 300
 *     aop-enabled: true
 * </pre>
 *
 * @author MANFOUO Braun
 */
@ConfigurationProperties(prefix = "tnt.roles")
public class TntRolesProperties {

    /**
     * The UUID of the system tenant used for seeding global/system-scoped roles.
     * Must be a stable, well-known UUID configured consistently across environments.
     */
    private UUID systemTenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * When true, {@link com.yowyob.tiibntick.core.roles.application.service.TntRoleInitializationService}
     * will seed TiiBnTick role definitions into the Kernel DB on startup.
     * Safe to enable in all environments (idempotent).
     */
    private boolean provisionOnStartup = true;

    /**
     * TTL in seconds for the Kernel's Redis permission cache.
     * Matches the {@code ReactivePermissionCache} configuration in the Kernel.
     * Default: 300 seconds (5 minutes).
     */
    private int permissionCacheTtlSeconds = 300;

    /**
     * When true, the {@link com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect}
     * AOP bean is registered, enabling {@code @RequirePermission} annotation enforcement.
     * Disable in test environments where AOP is not needed.
     */
    private boolean aopEnabled = true;

    /**
     * Controls how {@code ReactivePermissionResolver} resolves permissions —
     * see {@link PermissionResolutionMode}. Bound from {@code tnt.roles.permission.mode}.
     */
    private final Permission permission = new Permission();

    public UUID getSystemTenantId() { return systemTenantId; }
    public void setSystemTenantId(UUID systemTenantId) { this.systemTenantId = systemTenantId; }

    public boolean isProvisionOnStartup() { return provisionOnStartup; }
    public void setProvisionOnStartup(boolean provisionOnStartup) { this.provisionOnStartup = provisionOnStartup; }

    public int getPermissionCacheTtlSeconds() { return permissionCacheTtlSeconds; }
    public void setPermissionCacheTtlSeconds(int permissionCacheTtlSeconds) {
        this.permissionCacheTtlSeconds = permissionCacheTtlSeconds;
    }

    public boolean isAopEnabled() { return aopEnabled; }
    public void setAopEnabled(boolean aopEnabled) { this.aopEnabled = aopEnabled; }

    public Permission getPermission() { return permission; }

    /**
     * Nested {@code tnt.roles.permission.*} properties.
     */
    public static class Permission {

        /**
         * LOCAL (default, no Kernel dependency), REMOTE (Kernel HTTP only), or HYBRID
         * (local first, Kernel fallback). See {@link PermissionResolutionMode}.
         */
        private PermissionResolutionMode mode = PermissionResolutionMode.LOCAL;

        public PermissionResolutionMode getMode() { return mode; }
        public void setMode(PermissionResolutionMode mode) { this.mode = mode; }
    }
}
