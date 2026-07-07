package com.yowyob.tiibntick.core.accounting.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.accounting.adapter.out.persistence.entity.AccountEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for Account entities.
 * Author: MANFOUO Braun
 */
public interface AccountR2dbcRepository extends ReactiveCrudRepository<AccountEntity, UUID> {

    @Query("SELECT * FROM accounting.accounts WHERE tenant_id = :tenantId AND id = :id")
    Mono<AccountEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT * FROM accounting.accounts WHERE tenant_id = :tenantId AND code = :code")
    Mono<AccountEntity> findByTenantIdAndCode(UUID tenantId, String code);

    @Query("SELECT * FROM accounting.accounts WHERE tenant_id = :tenantId AND (:activeOnly = false OR active = true) ORDER BY code ASC")
    Flux<AccountEntity> findByTenantId(UUID tenantId, boolean activeOnly);

    @Query("SELECT * FROM accounting.accounts WHERE tenant_id = :tenantId AND category = :category ORDER BY code ASC")
    Flux<AccountEntity> findByTenantIdAndCategory(UUID tenantId, String category);

    @Query("SELECT COUNT(*) > 0 FROM accounting.accounts WHERE tenant_id = :tenantId AND code = :code")
    Mono<Boolean> existsByTenantIdAndCode(UUID tenantId, String code);
    /**
     * Finds all accounts owned by a specific FreelancerOrg ().
     */
    @Query("SELECT * FROM accounting.accounts WHERE tenant_id = :tenantId AND owner_org_id = :ownerOrgId ORDER BY code ASC")
    Flux<AccountEntity> findByTenantIdAndOwnerOrgId(UUID tenantId, String ownerOrgId);

}