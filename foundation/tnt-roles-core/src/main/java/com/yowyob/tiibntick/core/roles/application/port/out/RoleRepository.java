package com.yowyob.tiibntick.core.roles.application.port.out;

import com.yowyob.tiibntick.core.roles.domain.model.Role;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Secondary (outbound) port for {@link Role} persistence.
 *
 * <p>Local replacement for the Kernel's {@code RoleRepository} port — TiiBnTick no
 * longer shares Kernel Spring beans/types (see root {@code CLAUDE.md}: Kernel is
 * HTTP-only). Default implementation: {@link
 * com.yowyob.tiibntick.core.roles.adapter.out.persistence.InMemoryRoleRepository}.
 *
 * @author MANFOUO Braun
 */
public interface RoleRepository {

    Mono<Boolean> existsByCode(UUID tenantId, String code);

    Mono<Role> findById(UUID tenantId, UUID roleId);

    Flux<Role> findByTenantId(UUID tenantId);

    Mono<Role> save(Role role);

    Mono<Void> deleteById(UUID tenantId, UUID roleId);

    /**
     * Records the Kernel-side role id once {@link Role} has been successfully provisioned
     * into the Kernel (see {@code KernelRoleSyncWorker}).
     *
     * <p>Deliberately narrow: {@link Role} itself carries no {@code kernelRoleId} field —
     * that column exists only on the persistence entity (see {@code RoleEntity}) and is
     * meaningful only to the Kernel sync worker/reconciliation job, not to the rest of the
     * domain. A no-op for non-persistent implementations (e.g. the in-memory fallback),
     * which have nowhere to durably record it.
     *
     * @param tenantId     tenant the role belongs to
     * @param roleId       local role id ({@link Role#id()})
     * @param kernelRoleId the Kernel-side role UUID
     */
    Mono<Void> markKernelRoleId(UUID tenantId, UUID roleId, UUID kernelRoleId);

    /**
     * Reads back the Kernel-side role id recorded by {@link #markKernelRoleId}, if any.
     * Empty means either the role was never successfully synced to the Kernel yet, or (for
     * non-persistent implementations) that no such record exists at all — both cases mean
     * "nothing to delete Kernel-side" to a caller like {@code TntRoleManagementService}.
     */
    Mono<UUID> findKernelRoleId(UUID tenantId, UUID roleId);
}
