package com.yowyob.tiibntick.core.roles.adapter.out.persistence;

import com.yowyob.tiibntick.core.roles.adapter.out.persistence.mapper.RolePersistenceMapper;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.domain.model.Role;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implements {@link RoleRepository} on top of {@link R2dbcRoleEntityRepository}.
 *
 * <p>{@link #save(Role)} fetches the existing row first (if any) so that columns the
 * domain {@link Role} record doesn't carry ({@code systemRole}, {@code editable},
 * {@code kernelRoleId}, {@code createdAt}) are preserved across an update rather than
 * clobbered with defaults; a genuinely new role is inserted with those columns defaulted
 * (see {@link RolePersistenceMapper#toNewEntity}).
 *
 * <p>Not registered as a Spring bean here — instantiated explicitly by the wiring phase
 * (see {@code TntRolesAutoConfiguration}).
 *
 * @author MANFOUO Braun
 */
public class RoleRepositoryAdapter implements RoleRepository {

    private final R2dbcRoleEntityRepository repository;

    public RoleRepositoryAdapter(R2dbcRoleEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Boolean> existsByCode(UUID tenantId, String code) {
        return repository.existsByTenantIdAndCodeIgnoreCase(tenantId, code);
    }

    @Override
    public Mono<Role> findById(UUID tenantId, UUID roleId) {
        return repository.findByIdAndTenantId(roleId, tenantId)
                .map(RolePersistenceMapper::toDomain);
    }

    @Override
    public Flux<Role> findByTenantId(UUID tenantId) {
        return repository.findByTenantId(tenantId)
                .map(RolePersistenceMapper::toDomain);
    }

    @Override
    public Mono<Role> save(Role role) {
        return repository.findByIdAndTenantId(role.id(), role.tenantId())
                .flatMap(existing -> {
                    RolePersistenceMapper.applyDomainFieldsForUpdate(existing, role);
                    existing.markNotNew();
                    return repository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> repository.save(RolePersistenceMapper.toNewEntity(role))))
                .map(RolePersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> deleteById(UUID tenantId, UUID roleId) {
        return repository.deleteByIdAndTenantId(roleId, tenantId).then();
    }

    @Override
    public Mono<Void> markKernelRoleId(UUID tenantId, UUID roleId, UUID kernelRoleId) {
        return repository.findByIdAndTenantId(roleId, tenantId)
                .flatMap(existing -> {
                    existing.setKernelRoleId(kernelRoleId);
                    existing.markNotNew();
                    return repository.save(existing);
                })
                .then();
    }

    @Override
    public Mono<UUID> findKernelRoleId(UUID tenantId, UUID roleId) {
        return repository.findByIdAndTenantId(roleId, tenantId)
                .flatMap(entity -> Mono.justOrEmpty(entity.getKernelRoleId()));
    }
}
