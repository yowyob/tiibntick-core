package com.yowyob.tiibntick.common.security;

import java.util.Set;

/**
 * Pure, dependency-free implementation of TiiBnTick's {@code resource:action} permission
 * matching semantics — exact match, resource wildcard ({@code resource:*}), and global
 * wildcard ({@code *}).
 *
 * <p>Extracted here (2026-07-09) so it has exactly one definition, shared by:
 * <ul>
 *   <li>{@code tnt-roles-core}'s {@code TntPermissionEvaluator} — human/JWT-carrying user
 *       RBAC, which layers its own {@code #SCOPE} context-suffix handling
 *       ({@code #AGENCY:<id>}, {@code #SYSTEM}, {@code #TENANT}) on top of this base match.</li>
 *   <li>{@code tnt-platform-gateway-core}'s platform-client scope model — machine-to-machine
 *       scopes with no tenant/agency context, using the base match directly (see
 *       {@code docs/auth/platform-client-management-design.md} §2.4).</li>
 * </ul>
 *
 * <p>Deliberately has no Spring/Kernel/agency-scope awareness — callers that need the
 * {@code #SCOPE} suffix semantics extract the base permission (the part before {@code #})
 * before calling this class, and apply their own scope-matching rule on top.
 *
 * @author MANFOUO Braun
 */
public final class PermissionMatcher {

    private static final String GLOBAL_WILDCARD = "*";

    private PermissionMatcher() {
    }

    /**
     * Returns true if a single granted permission string satisfies {@code resource:action},
     * via exact match, resource wildcard ({@code resource:*}), or the global wildcard
     * ({@code *}).
     *
     * @param grantedPermission a single permission string, with any {@code #SCOPE} suffix
     *                          already stripped by the caller
     */
    public static boolean matchesBase(String grantedPermission, String resource, String action) {
        if (grantedPermission == null) {
            return false;
        }
        if (grantedPermission.equals(GLOBAL_WILDCARD)) {
            return true;
        }
        String exact = resource + ":" + action;
        String resourceWildcard = resource + ":*";
        return grantedPermission.equals(exact) || grantedPermission.equals(resourceWildcard);
    }

    /**
     * Returns true if any permission in {@code grantedPermissions} satisfies
     * {@code resource:action} (see {@link #matchesBase}). Convenience for callers with
     * no scope-suffix semantics to layer on top (e.g. platform-client M2M scopes).
     */
    public static boolean matchesAny(Set<String> grantedPermissions, String resource, String action) {
        if (grantedPermissions == null || grantedPermissions.isEmpty()) {
            return false;
        }
        for (String permission : grantedPermissions) {
            if (matchesBase(permission, resource, action)) {
                return true;
            }
        }
        return false;
    }
}
