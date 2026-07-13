package com.yowyob.tiibntick.core.gofp.application.service;

import com.yowyob.tiibntick.core.gofp.application.port.in.IMatchingUseCase;
import com.yowyob.tiibntick.core.gofp.application.port.out.IDeliveryPersonAvailabilityRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IFreelancerExtensionRepository;
import com.yowyob.tiibntick.core.gofp.domain.model.MatchingRequest;
import com.yowyob.tiibntick.core.gofp.domain.model.MatchingResult;
import com.yowyob.tiibntick.core.gofp.domain.model.RankingCriteria;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.VehicleType;
import com.yowyob.tiibntick.core.gofp.domain.policy.MatchingExpansionPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Orchestre le matching d'une annonce vers des livreurs candidats.
 *
 * Algorithme :
 *  1. Récupère les livreurs disponibles depuis gofp.delivery_person_availability
 *  2. Filtre Haversine dans le rayon courant
 *  3. Si pas assez de candidats → expansion (MatchingExpansionPolicy)
 *  4. Construit RankingCriteria en fusionnant disponibilité + extension Market
 *  5. Délègue le classement à TopsisRankingCoreService
 *
 * NOTE : tnt-actor-core et tnt-resource-core seront appelés ici via leurs ports
 * une fois que leurs interfaces de requête seront exposées. En attendant, les
 * données de réputation et de capacité proviennent de gofp.freelancer_extensions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingCoreService implements IMatchingUseCase {

    private final IDeliveryPersonAvailabilityRepository availabilityRepository;
    private final IFreelancerExtensionRepository        freelancerExtensionRepository;
    private final TopsisRankingCoreService              topsisRankingService;

    @Override
    public Flux<MatchingResult> matchAnnouncement(MatchingRequest request) {
        return rankCandidates(request)
            .collectList()
            .flatMap(results -> notifyCandidates(request.getAnnouncementId(), Flux.fromIterable(results))
                .thenReturn(results))
            .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<MatchingResult> rankCandidates(MatchingRequest request) {
        return findCandidatesWithExpansion(request, request.getInitialRadiusKm())
            .collectList()
            .flatMap(criteria -> topsisRankingService.rank(criteria, request.getRequiredVehicleType()))
            .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Void> notifyCandidates(UUID announcementId, Flux<MatchingResult> rankedCandidates) {
        // Notification déléguée à tnt-notify-core (intégration future via port)
        return rankedCandidates
            .doOnNext(r -> log.info("[MATCHING] Candidat notifié : actorId={} rank={} score={}",
                r.getFreelancerActorId(), r.getRank(), r.getTopsisScore()))
            .then();
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Expansion géographique récursive selon MatchingExpansionPolicy.
     */
    private Flux<RankingCriteria> findCandidatesWithExpansion(MatchingRequest request, double radiusKm) {
        return availabilityRepository.findAllAvailable()
            .filter(avail -> avail.getCurrentLat() != null && avail.getCurrentLon() != null)
            .filter(avail -> haversineKm(
                request.getPickupLat(), request.getPickupLon(),
                avail.getCurrentLat(), avail.getCurrentLon()) <= radiusKm)
            .collectList()
            .flatMapMany(availList -> {
                if (MatchingExpansionPolicy.shouldExpand(availList.size(), radiusKm)) {
                    Double nextRadius = MatchingExpansionPolicy.nextRadius(radiusKm);
                    if (nextRadius != null) {
                        log.info("[MATCHING] Expansion {} km → {} km (candidats={}/{})",
                            radiusKm, nextRadius, availList.size(),
                            MatchingExpansionPolicy.MIN_CANDIDATES_THRESHOLD);
                        return findCandidatesWithExpansion(request, nextRadius);
                    }
                }
                return Flux.fromIterable(availList)
                    .flatMap(avail -> buildCriteria(avail.getFreelancerActorId(),
                        avail.getCurrentLat(), avail.getCurrentLon(),
                        request.getPickupLat(), request.getPickupLon()));
            });
    }

    private Mono<RankingCriteria> buildCriteria(UUID freelancerActorId,
                                                 double candidateLat, double candidateLon,
                                                 double pickupLat, double pickupLon) {
        double distKm = haversineKm(pickupLat, pickupLon, candidateLat, candidateLon);

        return freelancerExtensionRepository.findByFreelancerActorId(freelancerActorId)
            .defaultIfEmpty(new com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.FreelancerExtensionEntity())
            .map(ext -> RankingCriteria.builder()
                .freelancerActorId(freelancerActorId)
                .distanceKm(distKm)
                // Réputation et capacité seront enrichies depuis tnt-actor-core / tnt-resource-core
                // via leurs ports dès que disponibles. Valeurs par défaut en attendant.
                .reputationScore(3.0)
                .capacityKg(50.0)
                .vehicleType(VehicleType.MOTO)
                .currentLat(candidateLat)
                .currentLon(candidateLon)
                .build());
    }

    /** Formule de Haversine — distance en km entre deux points GPS. */
    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
