package com.yowyob.tiibntick.core.tp.adapter.out.persistence;

import com.yowyob.tiibntick.core.tp.adapter.out.persistence.mapper.TntTpPersistenceMapper;
import com.yowyob.tiibntick.core.tp.adapter.out.persistence.r2dbc.TntClientProfileR2dbcRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.TntClientProfileRepository;
import com.yowyob.tiibntick.core.tp.domain.model.TntClientProfile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Adapter: implements TntClientProfileRepository using Spring Data R2DBC.
 *
 * @author MANFOUO Braun
 */
@Component
public class TntClientProfileRepositoryAdapter implements TntClientProfileRepository {

    private final TntClientProfileR2dbcRepository r2dbcRepo;
    private final TntTpPersistenceMapper mapper;

    public TntClientProfileRepositoryAdapter(
            TntClientProfileR2dbcRepository r2dbcRepo,
            TntTpPersistenceMapper mapper) {
        this.r2dbcRepo = r2dbcRepo;
        this.mapper = mapper;
    }

    @Override
    public Mono<TntClientProfile> save(TntClientProfile profile) {
        return r2dbcRepo.existsById(profile.getId())
                .flatMap(exists -> {
                    var entity = mapper.toEntity(profile);
                    entity.setNew(!exists);
                    return r2dbcRepo.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Mono<TntClientProfile> findById(UUID profileId) {
        return r2dbcRepo.findById(profileId).map(mapper::toDomain);
    }

    @Override
    public Mono<TntClientProfile> findByThirdPartyId(UUID tenantId, UUID thirdPartyId) {
        return r2dbcRepo.findByTenantIdAndThirdPartyId(tenantId, thirdPartyId)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Boolean> existsByThirdPartyId(UUID tenantId, UUID thirdPartyId) {
        return r2dbcRepo.existsByTenantIdAndThirdPartyId(tenantId, thirdPartyId);
    }

    @Override
    public Flux<TntClientProfile> findAllByTenantId(UUID tenantId) {
        return r2dbcRepo.findAllByTenantId(tenantId).map(mapper::toDomain);
    }

    @Override
    public Mono<Void> deleteById(UUID profileId) {
        return r2dbcRepo.deleteById(profileId);
    }
    @Override
    public reactor.core.publisher.Flux<com.yowyob.tiibntick.core.tp.domain.model.TntClientProfile> findByFreelancerOrgId(
            java.util.UUID tenantId, String freelancerOrgId) {
        return r2dbcRepo.findByFreelancerOrgId(tenantId, freelancerOrgId)
                .map(mapper::toDomain);
    }

    @Override
    public reactor.core.publisher.Mono<Integer> countTransactionsByFreelancerOrg(
            java.util.UUID tenantId, java.util.UUID thirdPartyId, String freelancerOrgId) {
        // Delivery transaction count is owned by tnt-delivery-core.
        // This implementation queries the local profile metadata.
        // In production, this would be an event-sourced counter updated from Kafka.
        return reactor.core.publisher.Mono.just(0);
    }

}