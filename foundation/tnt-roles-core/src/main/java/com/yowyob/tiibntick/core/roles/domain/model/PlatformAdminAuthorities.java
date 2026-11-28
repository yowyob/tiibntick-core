package com.yowyob.tiibntick.core.roles.domain.model;

import com.yowyob.tiibntick.common.security.PermissionMatcher;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The single definition of what a TiiBnTick platform administrator receives, and of what a Kernel token may
 * never bring by itself (decisions D-162, D-167).
 *
 * <p>Kernel role codes and permissions live per tenant, and a tenant owner can create any role code with any
 * permission in their own tenant: an entry of the token's {@code roles}/{@code permissions} claims that names
 * TNT_ADMIN, or a permission only TNT_ADMIN holds (or a wildcard covering one), is therefore never trusted.
 * TNT_ADMIN only comes from the Core: the system-tenant owner rule or the registry {@code tnt_platform_role_grants}.
 *
 * @author MANFOUO Braun
 */
public final class PlatformAdminAuthorities {

    public static final String ROLE_TNT_ADMIN = "ROLE_TNT_ADMIN";

    /**
     * Marker authority added only by the Core's own owner rule (token of the system tenant carrying the Kernel
     * owner permission). Lets the platform-admin API recognise the system-tenant owner without re-reading claims.
     */
    public static final String SYSTEM_TENANT_OWNER_MARKER = "TNT_SYSTEM_TENANT_OWNER";

    /** D-167: the same set for the owner and for every granted administrator — no business role. */
    public static final List<String> AUTHORITIES = List.of(
            ROLE_TNT_ADMIN,
            "accounting:read", "accounting:write", "accounting:admin",
            "sales:read", "sales:write",
            "administration:permissions:read",
            "administration:roles:read", "administration:roles:write",
            "administration:settings:read", "administration:settings:write",
            TntPermission.TNT_PLATFORM_ADMIN);

    /** Permissions no role but TNT_ADMIN holds (e.g. {@code system:admin}, {@code platform:clients}). */
    private static final Set<String> ADMIN_ONLY_PERMISSIONS = adminOnlyPermissions();

    private PlatformAdminAuthorities() {
    }

    /**
     * True when a token claim entry (a {@code roles} entry or a {@code permissions} entry, with or without a
     * {@code #SCOPE} suffix) would make its holder a platform administrator: it must be dropped.
     */
    public static boolean isForgeableAdminClaim(String entry) {
        if (entry == null) {
            return false;
        }
        String base = entry.contains("#") ? entry.substring(0, entry.indexOf('#')) : entry;
        String upper = base.trim().toUpperCase(Locale.ROOT);
        if (upper.equals(PlatformRoleGrant.TNT_ADMIN) || upper.equals(ROLE_TNT_ADMIN)
                || upper.equals(SYSTEM_TENANT_OWNER_MARKER)) {
            return true;
        }
        String trimmed = base.trim();
        if (trimmed.equals(TntPermission.ALL)) {
            return true;
        }
        for (String reserved : ADMIN_ONLY_PERMISSIONS) {
            int colon = reserved.indexOf(':');
            if (colon > 0 && PermissionMatcher.matchesBase(trimmed, reserved.substring(0, colon),
                    reserved.substring(colon + 1))) {
                return true;
            }
        }
        return false;
    }

    static Set<String> adminOnlyPermissions() {
        Set<String> heldByOthers = new HashSet<>();
        for (TntRole role : TntRole.values()) {
            if (role.scopeType() != RoleScopeType.SYSTEM) {
                heldByOthers.addAll(role.defaultPermissions());
            }
        }
        List<String> declared = new ArrayList<>();
        for (Field f : TntPermission.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && Modifier.isPublic(f.getModifiers())
                    && f.getType() == String.class) {
                try {
                    declared.add((String) f.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        Set<String> adminOnly = new HashSet<>();
        for (String permission : declared) {
            if (permission == null || permission.equals(TntPermission.ALL) || !permission.contains(":")) {
                continue;
            }
            int colon = permission.indexOf(':');
            String resource = permission.substring(0, colon);
            String action = permission.substring(colon + 1);
            boolean held = heldByOthers.stream().anyMatch(p -> PermissionMatcher.matchesBase(
                    p.contains("#") ? p.substring(0, p.indexOf('#')) : p, resource, action));
            if (!held) {
                adminOnly.add(permission);
            }
        }
        return Set.copyOf(adminOnly);
    }

    /** Exposed for tests and documentation. */
    public static Set<String> adminOnly() {
        return ADMIN_ONLY_PERMISSIONS;
    }
}
