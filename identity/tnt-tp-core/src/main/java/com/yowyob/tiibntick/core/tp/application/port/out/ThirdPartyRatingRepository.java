package com.yowyob.tiibntick.core.tp.application.port.out;

import com.yowyob.tiibntick.core.tp.domain.model.ThirdPartyRating;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Output port: persistence for ThirdPartyRating.
 *
 * @author MANFOUO Braun
 */
public interface ThirdPartyRatingRepository {

    Mono<ThirdPartyRating> save(ThirdPartyRating rating);

    Flux<ThirdPartyRating> findByRatedThirdPartyId(UUID tenantId, UUID ratedThirdPartyId);

    Mono<Boolean> existsByMissionIdAndRaterActorId(String missionId, UUID raterActorId);
}
