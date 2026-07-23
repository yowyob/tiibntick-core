package com.yowyob.tiibntick.core.roles.application.port.out;

import com.yowyob.tiibntick.core.roles.domain.model.TntRoleDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Secondary (outbound) port: provisions TiiBnTick role definitions into the Kernel's
 * role store ({@code RT-comops-roles-core} → {@code RoleRepository}).
 *
 * <p>Implemented by {@link com.yowyob.tiibntick.core.roles.adapter.out.kernel.KernelRoleProvisioningAdapter}
 * which calls the Kernel's {@code CreateRoleUseCase} and checks for duplicates.
 *
 * <p>Used by {@link com.yowyob.tiibntick.core.roles.application.service.TntRoleInitializationService}
 * at startup, and by {@code tnt-administration-core} when a new tenant is onboarded.
 *
 * @author MANFOUO Braun
 */
public interface ITntRoleProvisioningPort {

    /**
     * Provisions a single TiiBnTick role definition for the given tenant.
     * Skips silently if the role already exists (idempotent).
     *
     * @param tenantId   tenant to provision the role for
     * @param definition role to provision
     */
    Mono<Void> provisionRole(UUID tenantId, TntRoleDefinition definition);

    /**
     * Provisions all provided role definitions for the given tenant.
     * Each provisioning is independent — a failure on one does not block others.
     *
     * @param tenantId    tenant to provision roles for
     * @param definitions list of role definitions to provision
     */
    Flux<String> provisionAll(UUID tenantId, List<TntRoleDefinition> definitions);

    /**
     * Returns true if the role identified by {@code roleCode} is already
     * present in the Kernel's store for the given tenant.
     *
     * @param tenantId tenant scope
     * @param roleCode TiiBnTick role code
     */
    Mono<Boolean> roleExists(UUID tenantId, String roleCode);

    /**
     * Resolves the Kernel-side role UUID for a role definition already provisioned
     * under {@code tenantId}. Empty if no such role exists in the Kernel's store.
     *
     * @param tenantId tenant scope the role was provisioned under
     * @param roleCode TiiBnTick role code
     */
    Mono<UUID> findRoleId(UUID tenantId, String roleCode);

    /**
     * Invalidates the permission cache for a user after a role change.
     * Delegates to the Kernel's {@code ReactivePermissionCache} if available.
     *
     * @param tenantId tenant scope
     * @param userId   user whose cache should be invalidated
     */
    Mono<Void> invalidatePermissionCache(UUID tenantId, UUID userId);

    /**
     * Deletes a TiiBnTick role from the Kernel's role store.
     *
     * @param tenantId     tenant scope the role was provisioned under
     * @param kernelRoleId the Kernel-side role UUID (see {@link #findRoleId})
     */
    Mono<Void> deleteRole(UUID tenantId, UUID kernelRoleId);

    /**
     * Checks whether a role known to still be provisioned in the Kernel (by its Kernel-side
     * id) actually still exists there. Used by {@code KernelRoleReconciliationJob} — unlike
     * {@link #roleExists(UUID, String)}, which lists all roles for a tenant, this targets a
     * single already-known id via {@code GET /api/roles/{id}}, so it never has to look at
     * roles TiiBnTick doesn't already know about (the Kernel hosts roles from other Yowyob
     * solutions that must not be enumerated here).
     *
     * @param tenantId     tenant scope the role was provisioned under
     * @param kernelRoleId the Kernel-side role UUID
     * @return {@code true} if the Kernel confirms the role exists, {@code false} on a 404.
     *         On any other error (Kernel unreachable, etc.) resolves to {@code true} —
     *         fail-safe: reconciliation must never re-provision a role just because the
     *         existence check itself failed transiently.
     */
    Mono<Boolean> roleExistsById(UUID tenantId, UUID kernelRoleId);
}
