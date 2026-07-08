package com.yowyob.tiibntick.core.roles.application.port.out;

import com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Secondary (outbound) port for {@link UserRoleAssignment} persistence.
 *
 * <p>Local replacement for the Kernel's {@code UserRoleAssignmentRepository} port —
 * TiiBnTick no longer shares Kernel Spring beans/types (see root {@code CLAUDE.md}:
 * Kernel is HTTP-only). Default implementation: {@link
 * com.yowyob.tiibntick.core.roles.adapter.out.persistence.InMemoryUserRoleAssignmentRepository}.
 *
 * @author MANFOUO Braun
 */
public interface UserRoleAssignmentRepository {

    Mono<UserRoleAssignment> save(UserRoleAssignment assignment);

    Flux<UserRoleAssignment> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    Mono<UserRoleAssignment> findById(UUID tenantId, UUID assignmentId);

    Flux<UserRoleAssignment> findByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    Mono<Void> deleteById(UUID tenantId, UUID assignmentId);
}
