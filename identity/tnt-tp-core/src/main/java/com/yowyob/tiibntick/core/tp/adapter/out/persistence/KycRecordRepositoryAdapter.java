package com.yowyob.tiibntick.core.tp.adapter.out.persistence;

import com.yowyob.tiibntick.core.tp.adapter.out.persistence.mapper.TntTpPersistenceMapper;
import com.yowyob.tiibntick.core.tp.adapter.out.persistence.r2dbc.KycRecordR2dbcRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.KycRecordRepository;
import com.yowyob.tiibntick.core.tp.domain.model.KycRecord;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Adapter: implements KycRecordRepository using Spring Data R2DBC.
 *
 * @author MANFOUO Braun
 */
@Component
public class KycRecordRepositoryAdapter implements KycRecordRepository {

    private final KycRecordR2dbcRepository r2dbcRepo;
    private final TntTpPersistenceMapper mapper;

    public KycRecordRepositoryAdapter(KycRecordR2dbcRepository r2dbcRepo, TntTpPersistenceMapper mapper) {
        this.r2dbcRepo = r2dbcRepo;
        this.mapper = mapper;
    }

    @Override
    public Mono<KycRecord> save(KycRecord record) {
        return r2dbcRepo.existsById(record.getId())
                .flatMap(exists -> {
                    var entity = mapper.toEntity(record);
                    entity.setNew(!exists);
                    return r2dbcRepo.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Mono<KycRecord> findById(UUID kycRecordId) {
        return r2dbcRepo.findById(kycRecordId).map(mapper::toDomain);
    }

    @Override
    public Mono<KycRecord> findLatestByThirdPartyId(UUID tenantId, UUID thirdPartyId) {
        return r2dbcRepo.findLatestByTenantIdAndThirdPartyId(tenantId, thirdPartyId).map(mapper::toDomain);
    }

    @Override
    public Flux<KycRecord> findAllByThirdPartyId(UUID tenantId, UUID thirdPartyId) {
        return r2dbcRepo.findAllByTenantIdAndThirdPartyId(tenantId, thirdPartyId).map(mapper::toDomain);
    }

    @Override
    public Mono<Boolean> existsPendingByThirdPartyId(UUID tenantId, UUID thirdPartyId) {
        return r2dbcRepo.existsPendingByTenantIdAndThirdPartyId(tenantId, thirdPartyId);
    }
}
