package com.yowyob.tiibntick.core.gofp.domain.model;

import com.yowyob.tiibntick.core.gofp.domain.model.enums.VehicleType;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * Résultat de matching pour un livreur candidat.
 * Produit par TopsisRankingCoreService, consommé par MatchingCoreService.
 */
@Value
@Builder
public class MatchingResult {

    /** Actor ID du livreur (→ tnt-actor-core). */
    UUID freelancerActorId;

    /** Distance en km jusqu'au point de collecte. */
    double distanceKm;

    /** Note de réputation (0-5). */
    double reputation;

    /** Capacité du véhicule en kg. */
    double capacityKg;

    /** Type de véhicule. */
    VehicleType vehicleType;

    /** Score TOPSIS final (0-1, 1 = meilleur). */
    double topsisScore;

    /** Rang dans la liste triée (1 = meilleur). */
    int rank;
}
