package com.yowyob.tiibntick.core.tp.adapter.out.persistence.r2dbc;

import com.yowyob.tiibntick.core.tp.adapter.out.persistence.entity.TntClientProfileEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for TntClientProfileEntity.
 *
 * @author MANFOUO Braun
 */
public interface TntClientProfileR2dbcRepository
        extends ReactiveCrudRepository<TntClientProfileEntity, UUID> {

    @Query("SELECT * FROM tnt_client_profiles WHERE tenant_id = :tenantId AND third_party_id = :thirdPartyId")
    Mono<TntClientProfileEntity> findByTenantIdAndThirdPartyId(UUID tenantId, UUID thirdPartyId);

    @Query("SELECT COUNT(*) > 0 FROM tnt_client_profiles WHERE tenant_id = :tenantId AND third_party_id = :thirdPartyId")
    Mono<Boolean> existsByTenantIdAndThirdPartyId(UUID tenantId, UUID thirdPartyId);

    @Query("SELECT * FROM tnt_client_profiles WHERE tenant_id = :tenantId")
    Flux<TntClientProfileEntity> findAllByTenantId(UUID tenantId);
    /**
     * Finds all profiles linked to a specific FreelancerOrg ().
     * Queries the provider_links_json column for entries containing the FreelancerOrg ID.
     */
    @Query("SELECT * FROM tnt_client_profiles WHERE tenant_id = :tenantId AND provider_links_json::jsonb ->> 'FREELANCER_ORG' = :freelancerOrgId")
    Flux<com.yowyob.tiibntick.core.tp.adapter.out.persistence.entity.TntClientProfileEntity> findByFreelancerOrgId(UUID tenantId, String freelancerOrgId);

}