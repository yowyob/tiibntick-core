package com.yowyob.tiibntick.core.roles.adapter.out.persistence;

import com.yowyob.tiibntick.core.roles.adapter.out.persistence.mapper.RolePersistenceMapper;
import com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implements {@link UserRoleAssignmentRepository} on top of
 * {@link R2dbcUserRoleAssignmentEntityRepository}.
 *
 * <p>Assignments are effectively immutable once created ({@code UserRoleAssignment.assign}
 * always mints a fresh id), so {@link #save(UserRoleAssignment)} always inserts — unlike
 * {@code RoleRepositoryAdapter}, there is no existing-row fetch/merge step.
 *
 * <p>Not registered as a Spring bean here — instantiated explicitly by the wiring phase
 * (see {@code TntRolesAutoConfiguration}).
 *
 * @author MANFOUO Braun
 */
public class UserRoleAssignmentRepositoryAdapter implements UserRoleAssignmentRepository {

    private final R2dbcUserRoleAssignmentEntityRepository repository;

    public UserRoleAssignmentRepositoryAdapter(R2dbcUserRoleAssignmentEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<UserRoleAssignment> save(UserRoleAssignment assignment) {
        return repository.save(RolePersistenceMapper.toNewEntity(assignment))
                .map(RolePersistenceMapper::toDomain);
    }

    @Override
    public Flux<UserRoleAssignment> findByTenantIdAndUserId(UUID tenantId, UUID userId) {
        return repository.findByTenantIdAndUserId(tenantId, userId)
                .map(RolePersistenceMapper::toDomain);
    }

    @Override
    public Mono<UserRoleAssignment> findById(UUID tenantId, UUID assignmentId) {
        return repository.findByIdAndTenantId(assignmentId, tenantId)
                .map(RolePersistenceMapper::toDomain);
    }

    @Override
    public Flux<UserRoleAssignment> findByTenantIdAndRoleId(UUID tenantId, UUID roleId) {
        return repository.findByTenantIdAndRoleId(tenantId, roleId)
                .map(RolePersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> deleteById(UUID tenantId, UUID assignmentId) {
        return repository.deleteByIdAndTenantId(assignmentId, tenantId).then();
    }

    @Override
    public Mono<Void> markKernelAssignmentId(UUID tenantId, UUID assignmentId, UUID kernelAssignmentId) {
        return repository.findByIdAndTenantId(assignmentId, tenantId)
                .flatMap(existing -> {
                    existing.setKernelAssignmentId(kernelAssignmentId);
                    existing.markNotNew();
                    return repository.save(existing);
                })
                .then();
    }

    @Override
    public Mono<UUID> findKernelAssignmentId(UUID tenantId, UUID assignmentId) {
        return repository.findByIdAndTenantId(assignmentId, tenantId)
                .flatMap(entity -> Mono.justOrEmpty(entity.getKernelAssignmentId()));
    }
}
