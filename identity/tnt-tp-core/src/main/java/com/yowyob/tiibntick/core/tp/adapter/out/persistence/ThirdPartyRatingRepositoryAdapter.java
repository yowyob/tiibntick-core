package com.yowyob.tiibntick.core.tp.adapter.out.persistence;

import com.yowyob.tiibntick.core.tp.adapter.out.persistence.mapper.TntTpPersistenceMapper;
import com.yowyob.tiibntick.core.tp.adapter.out.persistence.r2dbc.ThirdPartyRatingR2dbcRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.ThirdPartyRatingRepository;
import com.yowyob.tiibntick.core.tp.domain.model.ThirdPartyRating;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Adapter: implements ThirdPartyRatingRepository using Spring Data R2DBC.
 *
 * @author MANFOUO Braun
 */
@Component
public class ThirdPartyRatingRepositoryAdapter implements ThirdPartyRatingRepository {

    private final ThirdPartyRatingR2dbcRepository r2dbcRepo;
    private final TntTpPersistenceMapper mapper;

    public ThirdPartyRatingRepositoryAdapter(ThirdPartyRatingR2dbcRepository r2dbcRepo, TntTpPersistenceMapper mapper) {
        this.r2dbcRepo = r2dbcRepo;
        this.mapper = mapper;
    }

    @Override
    public Mono<ThirdPartyRating> save(ThirdPartyRating rating) {
        return r2dbcRepo.existsById(rating.getId())
                .flatMap(exists -> {
                    var entity = mapper.toEntity(rating);
                    entity.setNew(!exists);
                    return r2dbcRepo.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Flux<ThirdPartyRating> findByRatedThirdPartyId(UUID tenantId, UUID ratedThirdPartyId) {
        return r2dbcRepo.findByTenantIdAndRatedThirdPartyId(tenantId, ratedThirdPartyId).map(mapper::toDomain);
    }

    @Override
    public Mono<Boolean> existsByMissionIdAndRaterActorId(String missionId, UUID raterActorId) {
        return r2dbcRepo.existsByMissionIdAndRaterActorId(missionId, raterActorId);
    }
}
