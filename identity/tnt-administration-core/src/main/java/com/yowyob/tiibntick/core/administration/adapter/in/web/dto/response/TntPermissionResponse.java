package com.yowyob.tiibntick.core.administration.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.administration.domain.model.TntPermissionEntry;

import java.util.UUID;

/**
 * API response DTO for a TiiBnTick permission entry.
 *
 * <p>Exposes the {@code kernelPermissionId} — the UUID of the corresponding permission in the
 * Yowyob Kernel (RT-comops-roles-core) — for cross-referencing purposes. Null for
 * TNT-exclusive system permissions (tnt:blockchain:mine, tnt:platform:admin, etc.).
 *
 * @author MANFOUO Braun
 */
public record TntPermissionResponse(
        String code,
        String name,
        String description,
        String module,
        String scope,
        boolean system,
        boolean assignable,
        /** Kernel permission UUID. Null for TNT-exclusive permissions. */
        UUID kernelPermissionId
) {
    public static TntPermissionResponse from(TntPermissionEntry p) {
        return new TntPermissionResponse(
                p.code(), p.name(), p.description(),
                p.module(), p.scope(), p.system(), p.assignable(),
                p.kernelPermissionId());
    }
}
