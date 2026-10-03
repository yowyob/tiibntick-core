package com.yowyob.tiibntick.core.roles.adapter.out.permission;

import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import com.yowyob.tiibntick.core.roles.domain.model.Role;
import com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves a user's effective permissions entirely from local data — no Kernel HTTP call.
 *
 * <p>Algorithm (swapping to {@link RemoteReactivePermissionResolver} later changes
 * nothing for callers):
 * <ol>
 *   <li>Look up the user's role assignments via {@link UserRoleAssignmentRepository}.</li>
 *   <li>For each assignment, resolve the {@link Role} via {@link RoleRepository} — first in the
 *       assignment's tenant, then in the system tenant where the canonical roles are
 *       provisioned (the system-tenant fallback only accepts a role whose code
 *       {@link TntRoleDefinitionRegistry} recognises as canonical: a custom role that merely
 *       happens to live under the system tenant is not lent to other tenants) — and take its
 *       persisted permission set — this is the tenant-customized source of truth once roles
 *       are actually provisioned into this repository.</li>
 *   <li>If a role can't be found there yet (nothing has provisioned it locally), fall back to
 *       {@link TntRoleDefinitionRegistry}'s default permissions, matched by role code — this
 *       is the "Niveau 2" fallback: the 9 canonical TiiBnTick roles always resolve even before
 *       any persistence-backed provisioning exists.</li>
 *   <li>Permissions from AGENCY/ORGANIZATION-scoped assignments are suffixed
 *       ({@code "#AGENCY:<scopeId>"} / {@code "#ORGANIZATION:<scopeId>"}) per
 *       {@code TntPermissionEvaluator}'s matching semantics; SYSTEM/TENANT-scoped assignments
 *       are left unsuffixed (global within the resource).</li>
 * </ol>
 *
 * <p>Returns an empty set (deny-by-default) when no assignment or role can be resolved —
 * consistent with the rest of this codebase's fail-closed posture.
 *
 * @author MANFOUO Braun
 */
public class LocalReactivePermissionResolver implements ReactivePermissionResolver {

    private static final Logger log = LoggerFactory.getLogger(LocalReactivePermissionResolver.class);

    private final UserRoleAssignmentRepository assignmentRepository;
    private final RoleRepository roleRepository;
    private final TntRoleDefinitionRegistry registry;
    private final UUID systemTenantId;

    /**
     * @param systemTenantId tenant under which the canonical role definitions are provisioned
     *                       ({@code tnt.roles.system-tenant-id}). {@code TntRoleAssignmentService}
     *                       assigns those system-tenant roles to users of <em>any</em> tenant, so
     *                       a role lookup that only searched the assignment's tenant resolved
     *                       every canonical assignment (e.g. FREELANCER) to zero permissions.
     */
    public LocalReactivePermissionResolver(
            UserRoleAssignmentRepository assignmentRepository,
            RoleRepository roleRepository,
            TntRoleDefinitionRegistry registry,
            UUID systemTenantId) {
        this.assignmentRepository = assignmentRepository;
        this.roleRepository = roleRepository;
        this.registry = registry;
        this.systemTenantId = systemTenantId;
    }

    @Override
    public Mono<Set<String>> resolvePermissions(UUID tenantId, UUID userId) {
        return assignmentRepository.findByTenantIdAndUserId(tenantId, userId)
                .flatMap(assignment -> resolveAssignment(tenantId, assignment))
                .collect(LinkedHashSet<String>::new, Set::addAll)
                .map(Set::copyOf)
                .doOnNext(perms -> log.debug(
                        "LOCAL resolution for tenant={} user={} -> {} permission(s)",
                        tenantId, userId, perms.size()));
    }

    private Flux<Set<String>> resolveAssignment(UUID tenantId, UserRoleAssignment assignment) {
        return roleRepository.findById(tenantId, assignment.roleId())
                // Canonical roles live under the system tenant (see TntRoleAssignmentService#assignRole).
                .switchIfEmpty(Mono.defer(() -> systemTenantId == null || systemTenantId.equals(tenantId)
                        ? Mono.empty()
                        : roleRepository.findById(systemTenantId, assignment.roleId())
                                .filter(role -> isCanonical(role, tenantId))))
                .map(role -> scoped(permissionsOf(role), assignment))
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    log.debug("No Role found locally for roleId={} — nothing to resolve without a role code.",
                            assignment.roleId());
                    return Set.<String>of();
                }))
                .flux();
    }

    /**
     * Only the canonical TiiBnTick roles are shared from the system tenant with every tenant;
     * any other system-tenant role stays private to the system tenant (deny-by-default).
     */
    private boolean isCanonical(Role role, UUID assignmentTenantId) {
        if (registry.isKnownRole(role.code())) return true;
        log.warn("Role {} ({}) exists only under the system tenant and is not canonical — "
                        + "not resolved for an assignment in tenant {}",
                role.code(), role.id(), assignmentTenantId);
        return false;
    }

    /**
     * "Niveau 2" — unions the persisted {@link Role}'s permissions with the canonical
     * {@link TntRoleDefinitionRegistry} defaults for that role's code. This means a role
     * provisioned with a partial/customized permission set still grants at least the
     * baseline the corresponding {@code TntRole} enum constant defines, and a role whose
     * persisted permissions haven't caught up with a newly added canonical permission
     * still resolves correctly.
     */
    private Set<String> permissionsOf(Role role) {
        Set<String> result = new LinkedHashSet<>(role.permissions());
        registry.findByCode(role.code()).ifPresent(def -> result.addAll(def.defaultPermissions()));
        return result;
    }

    private Set<String> scoped(Set<String> basePermissions, UserRoleAssignment assignment) {
        String suffix = switch (assignment.scopeType()) {
            case SYSTEM, TENANT -> null;
            case AGENCY -> "#AGENCY:" + assignment.scopeId();
            case ORGANIZATION -> "#ORGANIZATION:" + assignment.scopeId();
        };
        if (suffix == null) {
            return basePermissions;
        }
        Set<String> result = new LinkedHashSet<>();
        for (String permission : basePermissions) {
            result.add("*".equals(permission) ? permission : permission + suffix);
        }
        return result;
    }
}
