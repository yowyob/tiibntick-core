package com.yowyob.tiibntick.core.roles.adapter.out.permission;

import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import yowyob.comops.api.kernel.application.port.out.ReactivePermissionResolver;
import yowyob.comops.api.roles.application.port.out.RoleRepository;
import yowyob.comops.api.roles.application.port.out.UserRoleAssignmentRepository;
import yowyob.comops.api.roles.domain.model.Role;
import yowyob.comops.api.roles.domain.model.UserRoleAssignment;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves a user's effective permissions entirely from local data — no Kernel HTTP call.
 *
 * <p>Algorithm (mirrors the Kernel's own {@code RolesPermissionResolver} shape, so swapping
 * to {@link RemoteReactivePermissionResolver} later changes nothing for callers):
 * <ol>
 *   <li>Look up the user's role assignments via {@link UserRoleAssignmentRepository}.</li>
 *   <li>For each assignment, resolve the {@link Role} via {@link RoleRepository} and take its
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

    public LocalReactivePermissionResolver(
            UserRoleAssignmentRepository assignmentRepository,
            RoleRepository roleRepository,
            TntRoleDefinitionRegistry registry) {
        this.assignmentRepository = assignmentRepository;
        this.roleRepository = roleRepository;
        this.registry = registry;
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
                .map(role -> scoped(permissionsOf(role), assignment))
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    log.debug("No Role found locally for roleId={} — nothing to resolve without a role code.",
                            assignment.roleId());
                    return Set.<String>of();
                }))
                .flux();
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
