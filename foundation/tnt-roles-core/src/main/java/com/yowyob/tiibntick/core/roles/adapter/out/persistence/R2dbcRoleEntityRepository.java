package com.yowyob.tiibntick.core.roles.adapter.out.persistence;

import com.yowyob.tiibntick.core.roles.adapter.out.persistence.entity.RoleEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@code tnt_roles}. Left as a plain, {@code @Repository}-
 * annotated interface — Spring Data provides the proxy implementation; the later wiring
 * phase controls instantiation of {@code RoleRepositoryAdapter} explicitly via
 * {@code @Bean} methods in {@code TntRolesAutoConfiguration}, consistent with the rest of
 * that class.
 *
 * @author MANFOUO Braun
 */
@Repository
public interface R2dbcRoleEntityRepository extends ReactiveCrudRepository<RoleEntity, UUID> {

    Mono<RoleEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Flux<RoleEntity> findByTenantId(UUID tenantId);

    Mono<Boolean> existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

    Mono<Long> deleteByIdAndTenantId(UUID id, UUID tenantId);
}
