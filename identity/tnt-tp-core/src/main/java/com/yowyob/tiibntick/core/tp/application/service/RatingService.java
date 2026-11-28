package com.yowyob.tiibntick.core.tp.application.service;

import com.yowyob.tiibntick.core.tp.application.port.in.command.RateThirdPartyCommand;
import com.yowyob.tiibntick.core.tp.application.port.out.ThirdPartyRatingRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.TntClientProfileRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.TntTpEventPublisher;
import com.yowyob.tiibntick.core.tp.domain.event.TntTpDomainEvents.ThirdPartyRatedEvent;
import com.yowyob.tiibntick.core.tp.domain.model.ThirdPartyRating;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for third party rating management.
 *
 * @author MANFOUO Braun
 */
@Service
public class RatingService {

    private final ThirdPartyRatingRepository ratingRepository;
    private final TntClientProfileRepository profileRepository;
    private final TntTpEventPublisher eventPublisher;

    public RatingService(
            ThirdPartyRatingRepository ratingRepository,
            TntClientProfileRepository profileRepository,
            TntTpEventPublisher eventPublisher) {
        this.ratingRepository = ratingRepository;
        this.profileRepository = profileRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Records a new rating for a third party and updates the profile average.
     * Prevents duplicate ratings for the same mission by the same rater.
     */
    @RequirePermission(resource = "rating", action = "create")
    @Transactional
    public Mono<ThirdPartyRating> rate(RateThirdPartyCommand command) {
        // one rating per (mission, rater, kind of rated party): unique index uq_tnt_tp_rating_mission_rater_kind
        return ratingRepository.existsByDeliveryIdAndRaterActorIdAndSubjectKind(
                        command.deliveryId(), command.raterActorId(), command.subjectKind())
                .flatMap(alreadyRated -> {
                    if (Boolean.TRUE.equals(alreadyRated)) {
                        return Mono.error(new IllegalStateException(
                                "Actor " + command.raterActorId() + " has already rated the "
                                        + (command.subjectKind() != null ? command.subjectKind() + " of " : "")
                                        + "mission " + command.deliveryId()));
                    }
                    ThirdPartyRating rating = ThirdPartyRating.create(
                            command.tenantId(), command.ratedThirdPartyId(),
                            command.raterActorId(), command.deliveryId(),
                            command.score(), command.comment(),
                            command.ratingContext(), command.subjectKind());
                    // The tp-core client profile is optional: a rated party (delivery person, relay operator,
                    // or a client known only by its actor profile) may have none. The rating is kept either way.
                    return ratingRepository.save(rating)
                            .flatMap(saved -> profileRepository
                                    .findByThirdPartyId(command.tenantId(), command.ratedThirdPartyId())
                                    .flatMap(profile -> {
                                        var updated = profile.applyRating(command.score());
                                        return profileRepository.save(updated).thenReturn(updated);
                                    })
                                    .map(p -> p.getAverageRating() != null ? p.getAverageRating() : 0.0)
                                    .defaultIfEmpty(0.0)
                                    .flatMap(average -> {
                                        var event = new ThirdPartyRatedEvent(
                                                saved.getId(), saved.getTenantId(),
                                                saved.getRatedThirdPartyId(), saved.getMissionId(),
                                                saved.getScore(), average, Instant.now());
                                        return eventPublisher.publish(event).thenReturn(saved);
                                    }));
                });
    }

    @RequirePermission(resource = "rating", action = "read")
    public Flux<ThirdPartyRating> listRatings(UUID tenantId, UUID thirdPartyId) {
        return ratingRepository.findByRatedThirdPartyId(tenantId, thirdPartyId);
    }
}
