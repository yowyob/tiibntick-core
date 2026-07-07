package com.yowyob.tiibntick.core.tp.adapter.out.persistence;

import com.yowyob.tiibntick.core.tp.adapter.out.persistence.mapper.TntTpPersistenceMapper;
import com.yowyob.tiibntick.core.tp.adapter.out.persistence.r2dbc.LoyaltyAccountR2dbcRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.LoyaltyAccountRepository;
import com.yowyob.tiibntick.core.tp.domain.model.LoyaltyAccount;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Adapter: implements LoyaltyAccountRepository using Spring Data R2DBC.
 *
 * @author MANFOUO Braun
 */
@Component
public class LoyaltyAccountRepositoryAdapter implements LoyaltyAccountRepository {

    private final LoyaltyAccountR2dbcRepository r2dbcRepo;
    private final TntTpPersistenceMapper mapper;

    public LoyaltyAccountRepositoryAdapter(LoyaltyAccountR2dbcRepository r2dbcRepo, TntTpPersistenceMapper mapper) {
        this.r2dbcRepo = r2dbcRepo;
        this.mapper = mapper;
    }

    @Override
    public Mono<LoyaltyAccount> save(LoyaltyAccount account) {
        return r2dbcRepo.existsById(account.getId())
                .flatMap(exists -> {
                    var entity = mapper.toEntity(account);
                    entity.setNew(!exists);
                    return r2dbcRepo.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Mono<LoyaltyAccount> findByThirdPartyId(UUID tenantId, UUID thirdPartyId) {
        return r2dbcRepo.findByTenantIdAndThirdPartyId(tenantId, thirdPartyId).map(mapper::toDomain);
    }

    @Override
    public Mono<LoyaltyAccount> findById(UUID accountId) {
        return r2dbcRepo.findById(accountId).map(mapper::toDomain);
    }

    @Override
    public Mono<Boolean> existsByThirdPartyId(UUID tenantId, UUID thirdPartyId) {
        return r2dbcRepo.existsByTenantIdAndThirdPartyId(tenantId, thirdPartyId);
    }
}
