package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.actor.domain.model.PerformanceScore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.util.UUID;

@Service
public class ActorPerformanceService {

    private final IDelivererRepository delivererRepository;
    private final IFreelancerRepository freelancerRepository;

    public ActorPerformanceService(IDelivererRepository delivererRepository,
                                    IFreelancerRepository freelancerRepository) {
        this.delivererRepository = delivererRepository;
        this.freelancerRepository = freelancerRepository;
    }

    public Mono<PerformanceScore> computeScore(UUID tenantId, UUID actorId,
                                                ActorType actorType, YearMonth period) {
        return switch (actorType) {
            case PERMANENT_DELIVERER -> delivererRepository.findByActorId(tenantId, actorId)
                    .map(p -> PerformanceScore.of(
                            0,
                            p.rating().hasRatings() ? p.rating().score() / 5.0 : 0.0,
                            p.rating().score(),
                            period));
            case FREELANCER -> freelancerRepository.findByActorId(tenantId, actorId)
                    .map(p -> PerformanceScore.of(
                            0,
                            p.rating().hasRatings() ? p.rating().score() / 5.0 : 0.0,
                            p.rating().score(),
                            period));
            default -> Mono.error(new IllegalArgumentException(
                    "Performance score not available for actor type: " + actorType));
        };
    }
}
