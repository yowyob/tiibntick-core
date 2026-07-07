package com.yowyob.tiibntick.core.roles.application.port.in;

import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Primary (inbound) port: resolves the TiiBnTick roles held by a user.
 *
 * <p>Wraps the Kernel's {@code UserRoleAssignmentRepository} to return
 * typed {@link TntRole} values rather than raw strings. Only known TiiBnTick
 * roles are returned; unknown codes from the Kernel are silently filtered.
 *
 * @author MANFOUO Braun
 */
public interface ResolveUserRolesUseCase {

    /**
     * Returns the known TiiBnTick roles assigned to the user within the tenant.
     * Emits an empty flux if the user has no recognized TiiBnTick role.
     *
     * @param userId   user identifier
     * @param tenantId tenant scope
     */
    Flux<TntRole> resolveRoles(UUID userId, UUID tenantId);

    /**
     * Returns true if the user holds at least one assignment for the given TiiBnTick role code
     * within the tenant (any scope).
     *
     * @param userId   user identifier
     * @param tenantId tenant scope
     * @param roleCode TiiBnTick role code (e.g. "AGENCY_MANAGER")
     */
    Mono<Boolean> hasRoleAssignment(UUID userId, UUID tenantId, String roleCode);

    /**
     * Returns the highest-privilege TiiBnTick role held by the user within the tenant,
     * based on the standard role hierarchy: TNT_ADMIN > ORG_ADMIN > AGENCY_MANAGER > BRANCH_MANAGER
     * > SUPPORT_AGENT > PERMANENT_DELIVERER > RELAY_OPERATOR > FREELANCER > CLIENT.
     * Emits empty if the user has no TiiBnTick role.
     *
     * @param userId   user identifier
     * @param tenantId tenant scope
     */
    Mono<TntRole> resolveHighestRole(UUID userId, UUID tenantId);
}
