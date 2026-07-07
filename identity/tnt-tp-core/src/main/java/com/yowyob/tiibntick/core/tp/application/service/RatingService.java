package com.yowyob.tiibntick.core.tp.application.service;

import com.yowyob.tiibntick.core.tp.application.port.in.command.RateThirdPartyCommand;
import com.yowyob.tiibntick.core.tp.application.port.out.ThirdPartyRatingRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.TntClientProfileRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.TntTpEventPublisher;
import com.yowyob.tiibntick.core.tp.domain.event.TntTpDomainEvents.ThirdPartyRatedEvent;
import com.yowyob.tiibntick.core.tp.domain.exception.TntThirdPartyNotFoundException;
import com.yowyob.tiibntick.core.tp.domain.model.ThirdPartyRating;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

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
    @RequirePermission(resource = "actor", action = "write")
    public Mono<ThirdPartyRating> rate(RateThirdPartyCommand command) {
        return ratingRepository.existsByMissionIdAndRaterActorId(
                        command.missionId(), command.raterActorId())
                .flatMap(alreadyRated -> {
                    if (Boolean.TRUE.equals(alreadyRated)) {
                        return Mono.error(new IllegalStateException(
                                "Actor " + command.raterActorId()
                                        + " has already rated mission " + command.missionId()));
                    }
                    ThirdPartyRating rating = ThirdPartyRating.create(
                            command.tenantId(), command.ratedThirdPartyId(),
                            command.raterActorId(), command.missionId(),
                            command.score(), command.comment());
                    return ratingRepository.save(rating)
                            .flatMap(saved -> profileRepository
                                    .findByThirdPartyId(command.tenantId(), command.ratedThirdPartyId())
                                    .switchIfEmpty(Mono.error(
                                            new TntThirdPartyNotFoundException(command.ratedThirdPartyId())))
                                    .flatMap(profile -> {
                                        var updated = profile.applyRating(command.score());
                                        return profileRepository.save(updated).thenReturn(updated);
                                    })
                                    .flatMap(updatedProfile -> {
                                        var event = new ThirdPartyRatedEvent(
                                                saved.getId(), saved.getTenantId(),
                                                saved.getRatedThirdPartyId(), saved.getMissionId(),
                                                saved.getScore(),
                                                updatedProfile.getAverageRating() != null
                                                        ? updatedProfile.getAverageRating() : 0.0,
                                                Instant.now());
                                        return eventPublisher.publish(event).thenReturn(saved);
                                    }));
                });
    }

    @RequirePermission(resource = "actor", action = "read")
    public Flux<ThirdPartyRating> listRatings(UUID tenantId, UUID thirdPartyId) {
        return ratingRepository.findByRatedThirdPartyId(tenantId, thirdPartyId);
    }
}
