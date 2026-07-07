package com.yowyob.tiibntick.core.tp.adapter.out.persistence.r2dbc;

import com.yowyob.tiibntick.core.tp.adapter.out.persistence.entity.ThirdPartyRatingEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for ThirdPartyRatingEntity.
 *
 * @author MANFOUO Braun
 */
public interface ThirdPartyRatingR2dbcRepository
        extends ReactiveCrudRepository<ThirdPartyRatingEntity, UUID> {

    @Query("SELECT * FROM tnt_tp_ratings WHERE tenant_id = :tenantId AND rated_third_party_id = :ratedThirdPartyId ORDER BY created_at DESC")
    Flux<ThirdPartyRatingEntity> findByTenantIdAndRatedThirdPartyId(UUID tenantId, UUID ratedThirdPartyId);

    @Query("SELECT COUNT(*) > 0 FROM tnt_tp_ratings WHERE mission_id = :missionId AND rater_actor_id = :raterActorId")
    Mono<Boolean> existsByMissionIdAndRaterActorId(String missionId, UUID raterActorId);
}
