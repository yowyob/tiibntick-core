package com.yowyob.tiibntick.core.roles.application.port.in;

import com.yowyob.tiibntick.core.roles.domain.model.Role;
import com.yowyob.tiibntick.core.roles.domain.model.RoleScopeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

/**
 * Primary (inbound) port: CRUD for tenant-defined custom (non-system) roles.
 *
 * <p>The 9 canonical {@code TntRole} codes are system-owned and not manageable through this
 * port — {@code createRole}/{@code updateRole}/{@code deleteRole} all reject any code that
 * collides with a canonical role (see {@code TntRole#isKnownRole}).
 *
 * <p>Local RBAC persistence (Chantier D · Audit n°6 · S5): {@code createRole} writes locally
 * and enqueues a {@code RoleSyncOutboxEntry(PROVISION_ROLE)} for asynchronous Kernel sync.
 * {@code updateRole} is local-only by explicit product decision — the Kernel's
 * {@code role-controller} has no update endpoint, so permission-set edits cannot be synced;
 * the local {@code tnt_roles} row stays the authority for {@code @RequirePermission} checks.
 * {@code deleteRole} enqueues a {@code RoleSyncOutboxEntry(DELETE_ROLE)} only when the role
 * has a recorded Kernel id.
 *
 * @author MANFOUO Braun
 */
public interface ManageTntRoleUseCase {

    /**
     * Creates a new tenant-scoped custom role.
     *
     * @throws com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException if
     *         {@code code} collides with a canonical {@code TntRole} code
     */
    Mono<Role> createRole(UUID tenantId, String code, String name, RoleScopeType scopeType, Set<String> permissions);

    /**
     * Updates the name/permission set of an existing tenant-scoped custom role. Local-only —
     * never synced to the Kernel (see class Javadoc).
     *
     * @throws com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException if the role
     *         doesn't exist, or is a canonical system role
     */
    Mono<Role> updateRole(UUID tenantId, UUID roleId, String name, Set<String> permissions);

    /**
     * Deletes a tenant-scoped custom role.
     *
     * @throws com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException if the role
     *         doesn't exist, or is a canonical system role
     */
    Mono<Void> deleteRole(UUID tenantId, UUID roleId);

    /**
     * Lists every role (canonical and custom) recorded for {@code tenantId}.
     */
    Flux<Role> listRoles(UUID tenantId);
}
