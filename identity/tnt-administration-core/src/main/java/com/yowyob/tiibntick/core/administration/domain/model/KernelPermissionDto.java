package com.yowyob.tiibntick.core.administration.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Read-only DTO representing permission data fetched from the Yowyob Kernel
 * (RT-comops-roles-core, yow_kernel_db) via the KernelBridge HTTP client.
 *
 * <p>This record is <strong>never persisted</strong> in tnt_core_db. It is used to:
 * <ul>
 *   <li>Resolve {@code kernelPermissionId} for {@link TntPermissionEntry} entries that
 *       have a matching permission in the Kernel catalog.</li>
 *   <li>Cross-validate permission codes during role provisioning.</li>
 * </ul>
 *
 * <p>The Kernel's {@code AdministrationPermissionResponse} schema has no UUID field —
 * {@code permissionId} will always be {@code null} for catalog lookups. Callers must
 * use {@code code} as the stable cross-system identifier.
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelPermissionDto(
        /**
         * UUID of the permission in the Kernel (yow_kernel_db).
         * Not present in the Kernel's catalog response — always null for catalog lookups.
         * Retained for future compatibility if the Kernel exposes per-id endpoints.
         */
        UUID permissionId,

        /** Permission code as defined in the Kernel (e.g., "delivery:read"). */
        String code,

        /** Human-readable display name from the Kernel. */
        String name,

        /** Module this permission belongs to in the Kernel. */
        String module,

        /** Scope at which this permission is applicable. */
        String scope,

        /** Whether the Kernel considers this permission system-protected. */
        boolean system
) {}
