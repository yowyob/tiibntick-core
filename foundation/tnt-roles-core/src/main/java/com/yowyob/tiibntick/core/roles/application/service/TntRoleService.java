package com.yowyob.tiibntick.core.roles.application.service;

import com.yowyob.tiibntick.core.roles.application.port.in.ResolveUserRolesUseCase;
import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import yowyob.comops.api.roles.application.port.out.RoleRepository;
import yowyob.comops.api.roles.application.port.out.UserRoleAssignmentRepository;

import java.util.Comparator;
import java.util.UUID;

/**
 * Resolves TiiBnTick business roles assigned to a user via the Kernel's repositories.
 *
 * <p>Wraps {@code UserRoleAssignmentRepository} and {@code RoleRepository} from
 * {@code RT-comops-roles-core} to return strongly-typed {@link TntRole} values.
 * Only recognized TiiBnTick role codes are returned — unrecognized Kernel roles
 * are silently filtered, preserving forward compatibility.
 *
 * @author MANFOUO Braun
 */
public class TntRoleService implements ResolveUserRolesUseCase {

    private final UserRoleAssignmentRepository assignmentRepository;
    private final RoleRepository roleRepository;
    private final TntRoleDefinitionRegistry registry;

    public TntRoleService(
            UserRoleAssignmentRepository assignmentRepository,
            RoleRepository roleRepository,
            TntRoleDefinitionRegistry registry) {
        this.assignmentRepository = assignmentRepository;
        this.roleRepository = roleRepository;
        this.registry = registry;
    }

    @Override
    public Flux<TntRole> resolveRoles(UUID userId, UUID tenantId) {
        return assignmentRepository.findByTenantIdAndUserId(tenantId, userId)
                .flatMap(assignment -> roleRepository.findById(tenantId, assignment.roleId()))
                .map(role -> role.code())
                .filter(registry::isKnownRole)
                .distinct()
                .map(TntRole::fromCode);
    }

    @Override
    public Mono<Boolean> hasRoleAssignment(UUID userId, UUID tenantId, String roleCode) {
        if (!registry.isKnownRole(roleCode)) {
            return Mono.just(false);
        }
        return assignmentRepository.findByTenantIdAndUserId(tenantId, userId)
                .flatMap(assignment -> roleRepository.findById(tenantId, assignment.roleId()))
                .filter(role -> role.code().equalsIgnoreCase(roleCode))
                .hasElements();
    }

    @Override
    public Mono<TntRole> resolveHighestRole(UUID userId, UUID tenantId) {
        return resolveRoles(userId, tenantId)
                .collectList()
                .flatMap(roles -> {
                    if (roles.isEmpty()) {
                        return Mono.empty();
                    }
                    return Mono.just(roles.stream()
                            .min(Comparator.comparingInt(r -> registry.hierarchyIndex(r.code())))
                            .orElseThrow());
                });
    }
}
