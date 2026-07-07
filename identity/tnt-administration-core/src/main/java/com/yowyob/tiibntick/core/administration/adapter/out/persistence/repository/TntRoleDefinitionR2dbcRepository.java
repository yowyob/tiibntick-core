package com.yowyob.tiibntick.core.administration.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.administration.adapter.out.persistence.entity.TntRoleDefinitionEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@link TntRoleDefinitionEntity}.
 *
 * @author MANFOUO Braun
 */
public interface TntRoleDefinitionR2dbcRepository
        extends ReactiveCrudRepository<TntRoleDefinitionEntity, UUID> {

    @Query("SELECT * FROM administration.tnt_role_definitions WHERE tenant_id = :tenantId")
    Flux<TntRoleDefinitionEntity> findAllByTenantId(UUID tenantId);

    @Query("SELECT * FROM administration.tnt_role_definitions WHERE tenant_id = :tenantId AND template_code = :templateCode")
    Mono<TntRoleDefinitionEntity> findByTenantIdAndTemplateCode(UUID tenantId, String templateCode);

    @Query("SELECT COUNT(*) > 0 FROM administration.tnt_role_definitions WHERE tenant_id = :tenantId AND template_code = :templateCode")
    Mono<Boolean> existsByTenantIdAndTemplateCode(UUID tenantId, String templateCode);

    @Query("SELECT * FROM administration.tnt_role_definitions WHERE kernel_synced = false")
    Flux<TntRoleDefinitionEntity> findAllPendingKernelSync();
}
