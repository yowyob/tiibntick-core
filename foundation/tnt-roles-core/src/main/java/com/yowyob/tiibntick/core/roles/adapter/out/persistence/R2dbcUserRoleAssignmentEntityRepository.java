package com.yowyob.tiibntick.core.roles.adapter.out.persistence;

import com.yowyob.tiibntick.core.roles.adapter.out.persistence.entity.UserRoleAssignmentEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@code tnt_user_role_assignments}. See
 * {@link R2dbcRoleEntityRepository} for the note on why this stays a plain interface.
 *
 * @author MANFOUO Braun
 */
@Repository
public interface R2dbcUserRoleAssignmentEntityRepository extends ReactiveCrudRepository<UserRoleAssignmentEntity, UUID> {

    Flux<UserRoleAssignmentEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    Mono<UserRoleAssignmentEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Flux<UserRoleAssignmentEntity> findByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    Mono<Long> deleteByIdAndTenantId(UUID id, UUID tenantId);
}
