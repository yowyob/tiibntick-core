package com.yowyob.tiibntick.core.roles.config;

/**
 * Strategy used by the {@code ReactivePermissionResolver} bean to resolve a user's
 * effective permission set.
 *
 * <p>Bound from {@code tnt.roles.permission.mode}. The Kernel does not yet expose a
 * REST endpoint for permission resolution — {@link #REMOTE} and {@link #HYBRID} are
 * forward-compatible: business code using {@code @RequirePermission} never changes
 * when the switch is flipped once that endpoint ships.
 *
 * @author MANFOUO Braun
 */
public enum PermissionResolutionMode {

    /** Resolve entirely from local data: role assignments + role definitions, no Kernel HTTP call. */
    LOCAL,

    /** Resolve entirely via the Kernel's permission-resolution HTTP endpoint. */
    REMOTE,

    /** Try local resolution first; fall back to the Kernel over HTTP when local yields nothing. */
    HYBRID
}
