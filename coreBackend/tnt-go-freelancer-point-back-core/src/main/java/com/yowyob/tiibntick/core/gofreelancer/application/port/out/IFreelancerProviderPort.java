package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.application.usecase.TopsisRankingUseCase.FreelancerCandidate;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Outbound port supplying active freelancer candidates near a geo point
 * for the TOPSIS matching pipeline.
 *
 * @author MANFOUO BRAUN
 */
public interface IFreelancerProviderPort {

    /**
     * Returns active/available freelancers within {@code radiusKm} of the given point.
     * Implementations may use Elasticsearch geo-near with SQL fallback.
     */
    Mono<List<FreelancerCandidate>> findActiveCandidatesNear(double latitude, double longitude, double radiusKm);
}
