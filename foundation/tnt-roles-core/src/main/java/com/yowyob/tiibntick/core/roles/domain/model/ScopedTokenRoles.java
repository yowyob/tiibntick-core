package com.yowyob.tiibntick.core.roles.domain.model;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Decision D-176: an agency or organisation role of TiiBnTick ({@link RoleScopeType#AGENCY} /
 * {@link RoleScopeType#ORGANIZATION}) read from a Kernel token is honoured only when the Core's own assignments confirm
 * it — a tenant owner can create and assign any role code in their own Kernel tenant, so the token alone proves
 * nothing. This class turns a claim entry into the canonical form the confirmation compares:
 *
 * <ul>
 *   <li>{@code AGENCY_MANAGER}, {@code ROLE_AGENCY_MANAGER}, {@code ROLE_AGENCY_MANAGER#TENANT:<id>} →
 *       {@code AGENCY_MANAGER} (any confirmed assignment of that role in the token's tenant);</li>
 *   <li>{@code ROLE_AGENCY_MANAGER#AGENCY:<id>} → {@code AGENCY_MANAGER#AGENCY:<id>} (an assignment on that very
 *       agency); likewise {@code #ORGANIZATION:<id>} (also written {@code #ORG:<id>}).</li>
 * </ul>
 *
 * Anything else (tenant-scoped roles such as CLIENT or FREELANCER, Kernel-only codes such as AGENCY_ADMIN, plain
 * permissions) is not concerned and is left as it is.
 */
public final class ScopedTokenRoles {

    private ScopedTokenRoles() {
    }

    /** The canonical entry to confirm, or empty when {@code claimEntry} is not an agency/organisation role. */
    public static Optional<String> entryOf(String claimEntry) {
        if (claimEntry == null || claimEntry.isBlank()) {
            return Optional.empty();
        }
        String s = claimEntry.trim();
        if (s.regionMatches(true, 0, "ROLE_", 0, 5)) {
            s = s.substring(5);
        }
        int hash = s.indexOf('#');
        String code = (hash < 0 ? s : s.substring(0, hash)).toUpperCase(Locale.ROOT);
        if (!TntRole.isKnownRole(code)) {
            return Optional.empty();
        }
        RoleScopeType scope = TntRole.fromCode(code).scopeType();
        if (scope != RoleScopeType.AGENCY && scope != RoleScopeType.ORGANIZATION) {
            return Optional.empty();
        }
        if (hash < 0) {
            return Optional.of(code);
        }
        String suffix = s.substring(hash + 1);
        int colon = suffix.indexOf(':');
        String kind = (colon < 0 ? suffix : suffix.substring(0, colon)).toUpperCase(Locale.ROOT);
        String id = colon < 0 ? "" : suffix.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
        return switch (kind) {
            case "AGENCY" -> Optional.of(id.isEmpty() ? code : code + "#AGENCY:" + id);
            case "ORGANIZATION", "ORG" -> Optional.of(id.isEmpty() ? code : code + "#ORGANIZATION:" + id);
            default -> Optional.of(code);
        };
    }

    /** Canonical entries proven by one Core assignment: the bare code, and the code on its scope. */
    public static Set<String> provenBy(String roleCode, RoleScopeType scope, java.util.UUID scopeId) {
        String code = roleCode.toUpperCase(Locale.ROOT);
        if (scopeId == null || (scope != RoleScopeType.AGENCY && scope != RoleScopeType.ORGANIZATION)) {
            return Set.of(code);
        }
        return Set.of(code, code + "#" + scope.name() + ":" + scopeId.toString().toLowerCase(Locale.ROOT));
    }

    /** Whether a claim entry may be kept, given the canonical entries the Core confirmed for this user and tenant. */
    public static boolean confirmed(String claimEntry, Set<String> confirmedEntries) {
        return entryOf(claimEntry).map(confirmedEntries::contains).orElse(true);
    }
}
