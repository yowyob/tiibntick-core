package com.yowyob.tiibntick.core.administration.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Set;
import java.util.UUID;

/**
 * Read-only DTO representing role data fetched from the Yowyob Kernel
 * (RT-comops-roles-core, yow_kernel_db) via the KernelBridge HTTP client.
 *
 * <p>This record is <strong>never persisted</strong> in tnt_core_db. It is used only for:
 * <ul>
 *   <li>Validation: confirm a Kernel role exists before creating a TntRoleDefinition.</li>
 *   <li>Enrichment: display Kernel role metadata alongside TNT role template data.</li>
 * </ul>
 *
 * <p>Field mapping vs Kernel {@code RoleResponse} schema:
 * <ul>
 *   <li>{@code roleId} ← Kernel field {@code id}</li>
 *   <li>{@code permissionCodes} ← Kernel field {@code permissions}</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelRoleDto(
        /** UUID of the role in the Kernel (yow_kernel_db). Mapped from Kernel field "id". */
        @JsonAlias("id")
        UUID roleId,

        /** Role code as defined in the Kernel (e.g., "ROLE_ORG_ADMIN"). */
        String code,

        /** Human-readable display name from the Kernel. */
        String name,

        /** Scope type as defined in the Kernel (TENANT, ORGANIZATION, AGENCY, SYSTEM). */
        String scopeType,

        /** Tenant this role belongs to (null for global/system roles). */
        UUID tenantId,

        /** Permission codes assigned to this role. Mapped from Kernel field "permissions". */
        @JsonAlias("permissions")
        Set<String> permissionCodes
) {}
