package com.yowyob.tiibntick.core.administration.domain.model;

import java.util.UUID;

/**
 * Represents a single TiiBnTick-specific permission entry in the TNT catalog.
 *
 * <p>Each entry carries an optional {@code kernelPermissionId} which is the UUID of the
 * corresponding permission in the Yowyob Kernel (RT-comops-roles-core). This field is
 * null for TNT-exclusive permissions that have no Kernel counterpart (e.g., tnt:blockchain:mine).
 *
 * <p>This record is never persisted — it lives entirely in the in-memory catalog built
 * by {@link TntPermissionCatalog#buildCatalog()}.
 *
 * @author MANFOUO Braun
 */
public record TntPermissionEntry(
        /** TNT permission code, e.g. "delivery:read", "tnt:platform:admin". */
        String code,

        /** Human-readable display name. */
        String name,

        /** Short description of what the permission grants. */
        String description,

        /** Functional module this permission belongs to (DELIVERY, BLOCKCHAIN, etc.). */
        String module,

        /** Scope at which this permission is applicable (AGENCY, ORGANIZATION, TENANT, SYSTEM). */
        String scope,

        /** True if this permission is system-protected and cannot be self-assigned. */
        boolean system,

        /** True if this permission can be assigned to roles via the admin UI. */
        boolean assignable,

        /**
         * Optional UUID reference to the corresponding permission in the Kernel catalog
         * (RT-comops-roles-core, yow_kernel_db). Null when no Kernel counterpart exists.
         * Logical reference only — no physical FK cross-database.
         */
        UUID kernelPermissionId
) {
    /**
     * Convenience constructor for permissions that have no Kernel counterpart.
     * Sets kernelPermissionId to null.
     */
    public TntPermissionEntry(String code, String name, String description,
                               String module, String scope, boolean system, boolean assignable) {
        this(code, name, description, module, scope, system, assignable, null);
    }

    /** Returns a copy of this entry with the given Kernel permission UUID set. */
    public TntPermissionEntry withKernelPermissionId(UUID id) {
        return new TntPermissionEntry(code, name, description, module, scope, system, assignable, id);
    }
}
