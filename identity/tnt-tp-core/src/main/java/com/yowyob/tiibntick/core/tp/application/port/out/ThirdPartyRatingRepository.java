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

    Mono<Boolean> existsByDeliveryIdAndRaterActorId(String deliveryId, UUID raterActorId);

    /**
     * Whether the rater already rated this kind of party for the mission (lot L3): a client may rate the courier
     * and the relay operator of the same delivery. A null kind matches only ratings without kind.
     */
    Mono<Boolean> existsByDeliveryIdAndRaterActorIdAndSubjectKind(String deliveryId, UUID raterActorId,
                                                                 String subjectKind);
}
