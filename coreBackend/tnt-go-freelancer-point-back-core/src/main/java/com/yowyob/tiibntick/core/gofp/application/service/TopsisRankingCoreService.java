package com.yowyob.tiibntick.core.gofp.application.service;

import com.yowyob.tiibntick.core.gofp.domain.model.MatchingResult;
import com.yowyob.tiibntick.core.gofp.domain.model.RankingCriteria;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.VehicleType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Algorithme TOPSIS + AHP pour le classement des livreurs candidats.
 *
 * Critères (4) et poids AHP :
 *   1. Proximité au pickup   — 55.8%  — COST  (minimiser)
 *   2. Réputation            — 26.4%  — BENEFIT (maximiser)
 *   3. Capacité véhicule     — 12.2%  — BENEFIT (maximiser)
 *   4. Type de véhicule      — 5.7%   — BENEFIT (score relatif ou binaire)
 */
@Slf4j
@Service
public class TopsisRankingCoreService {

    private static final double W_PROXIMITY    = 0.558;
    private static final double W_REPUTATION   = 0.264;
    private static final double W_CAPACITY     = 0.122;
    private static final double W_VEHICLE_TYPE = 0.057;

    /**
     * Classe les candidats et retourne les résultats triés du meilleur au moins bon.
     *
     * @param candidates         critères bruts de chaque candidat
     * @param requiredVehicleType type requis (null = tous acceptés)
     */
    public Mono<List<MatchingResult>> rank(List<RankingCriteria> candidates,
                                           VehicleType requiredVehicleType) {
        if (candidates == null || candidates.isEmpty()) {
            return Mono.just(List.of());
        }

        // Filtre par type de véhicule si précisé
        List<RankingCriteria> filtered = (requiredVehicleType == null)
            ? candidates
            : candidates.stream()
                .filter(c -> requiredVehicleType == c.getVehicleType())
                .toList();

        if (filtered.isEmpty()) {
            log.warn("[TOPSIS] Aucun candidat avec le type {} parmi {}", requiredVehicleType, candidates.size());
            return Mono.just(List.of());
        }

        if (filtered.size() == 1) {
            RankingCriteria c = filtered.get(0);
            return Mono.just(List.of(MatchingResult.builder()
                .freelancerActorId(c.getFreelancerActorId())
                .distanceKm(c.getDistanceKm())
                .reputation(c.getReputationScore())
                .capacityKg(c.getCapacityKg())
                .vehicleType(c.getVehicleType())
                .topsisScore(1.0)
                .rank(1)
                .build()));
        }

        return Mono.just(applyTopsis(filtered, requiredVehicleType));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private List<MatchingResult> applyTopsis(List<RankingCriteria> candidates,
                                              VehicleType requiredVehicleType) {
        int n = candidates.size();

        // Normalisation vectorielle
        double sumDistSq   = candidates.stream().mapToDouble(c -> c.getDistanceKm()     * c.getDistanceKm()).sum();
        double sumRepSq    = candidates.stream().mapToDouble(c -> c.getReputationScore() * c.getReputationScore()).sum();
        double sumCapSq    = candidates.stream().mapToDouble(c -> c.getCapacityKg()      * c.getCapacityKg()).sum();

        double normDist = Math.sqrt(sumDistSq);
        double normRep  = Math.sqrt(sumRepSq);
        double normCap  = Math.sqrt(sumCapSq);

        // Score type de véhicule (relatif si pas de filtre, neutre si filtre)
        boolean vehicleFilterApplied = (requiredVehicleType != null);
        double[] vehicleScores = vehicleFilterApplied
            ? new double[n] // tous à 0 → neutres
            : computeRelativeVehicleScores(candidates);

        double sumVehicleSq = 0;
        for (double v : vehicleScores) sumVehicleSq += v * v;
        double normVehicle = Math.sqrt(sumVehicleSq);
        if (normVehicle == 0) normVehicle = 1;

        // Matrice normalisée pondérée
        double[] wDist   = new double[n];
        double[] wRep    = new double[n];
        double[] wCap    = new double[n];
        double[] wVehicle= new double[n];

        for (int i = 0; i < n; i++) {
            wDist[i]    = W_PROXIMITY    * (normDist   > 0 ? candidates.get(i).getDistanceKm()     / normDist    : 0);
            wRep[i]     = W_REPUTATION   * (normRep    > 0 ? candidates.get(i).getReputationScore() / normRep     : 0);
            wCap[i]     = W_CAPACITY     * (normCap    > 0 ? candidates.get(i).getCapacityKg()      / normCap     : 0);
            wVehicle[i] = W_VEHICLE_TYPE * vehicleScores[i] / normVehicle;
        }

        // Solutions idéale (best) et anti-idéale (worst)
        // Distance = COST → min est idéal ; Réputation, Capacité, Type = BENEFIT → max est idéal
        double bestDist = Double.MAX_VALUE, worstDist = 0;
        double bestRep  = 0, worstRep  = Double.MAX_VALUE;
        double bestCap  = 0, worstCap  = Double.MAX_VALUE;
        double bestVeh  = 0, worstVeh  = Double.MAX_VALUE;

        for (int i = 0; i < n; i++) {
            bestDist  = Math.min(bestDist,  wDist[i]);
            worstDist = Math.max(worstDist, wDist[i]);
            bestRep   = Math.max(bestRep,   wRep[i]);
            worstRep  = Math.min(worstRep,  wRep[i]);
            bestCap   = Math.max(bestCap,   wCap[i]);
            worstCap  = Math.min(worstCap,  wCap[i]);
            bestVeh   = Math.max(bestVeh,   wVehicle[i]);
            worstVeh  = Math.min(worstVeh,  wVehicle[i]);
        }

        // Score de proximité relative à la solution idéale
        double[] scores = new double[n];
        for (int i = 0; i < n; i++) {
            double dBest = Math.sqrt(
                sq(wDist[i]    - bestDist)  +
                sq(wRep[i]     - bestRep)   +
                sq(wCap[i]     - bestCap)   +
                sq(wVehicle[i] - bestVeh));
            double dWorst = Math.sqrt(
                sq(wDist[i]    - worstDist) +
                sq(wRep[i]     - worstRep)  +
                sq(wCap[i]     - worstCap)  +
                sq(wVehicle[i] - worstVeh));
            scores[i] = (dBest + dWorst) > 0 ? dWorst / (dBest + dWorst) : 0;
        }

        // Tri décroissant par score TOPSIS
        Integer[] indices = IntStream.range(0, n).boxed().toArray(Integer[]::new);
        java.util.Arrays.sort(indices, Comparator.comparingDouble((Integer i) -> scores[i]).reversed());

        return IntStream.range(0, n).mapToObj(rank -> {
            int idx = indices[rank];
            RankingCriteria c = candidates.get(idx);
            return MatchingResult.builder()
                .freelancerActorId(c.getFreelancerActorId())
                .distanceKm(c.getDistanceKm())
                .reputation(c.getReputationScore())
                .capacityKg(c.getCapacityKg())
                .vehicleType(c.getVehicleType())
                .topsisScore(scores[idx])
                .rank(rank + 1)
                .build();
        }).toList();
    }

    private double[] computeRelativeVehicleScores(List<RankingCriteria> candidates) {
        // Score ordinal par type (CAMION > CAMIONNETTE > VOITURE > MOTO > VELO > AUTRE)
        double[] scores = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            scores[i] = vehicleTypeOrdinalScore(candidates.get(i).getVehicleType());
        }
        return scores;
    }

    private double vehicleTypeOrdinalScore(VehicleType type) {
        if (type == null) return 0;
        return switch (type) {
            case CAMION       -> 6;
            case CAMIONNETTE  -> 5;
            case VOITURE      -> 4;
            case TRICYCLE     -> 3;
            case MOTO         -> 2;
            case VELO         -> 1;
            default           -> 0;
        };
    }

    private double sq(double x) { return x * x; }
}
