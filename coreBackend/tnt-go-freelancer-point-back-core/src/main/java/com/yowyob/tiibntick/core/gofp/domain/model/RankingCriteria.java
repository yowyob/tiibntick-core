package com.yowyob.tiibntick.core.gofp.domain.model;

import com.yowyob.tiibntick.core.gofp.domain.model.enums.VehicleType;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * Critères bruts d'un candidat livreur pour le calcul TOPSIS.
 * Fusionné depuis tnt-actor-core (FreelancerProfile) + tnt-resource-core (Vehicle)
 * + gofp.freelancer_extensions.
 */
@Value
@Builder
public class RankingCriteria {

    UUID freelancerActorId;

    /** Distance en km jusqu'au pickup (critère COST — à minimiser). */
    double distanceKm;

    /** Note de réputation 0-5 (critère BENEFIT — à maximiser). */
    double reputationScore;

    /** Capacité du véhicule en kg (critère BENEFIT). */
    double capacityKg;

    /** Type de véhicule (critère BENEFIT — score relatif ou binaire). */
    VehicleType vehicleType;

    /** Latitude courante du livreur. */
    double currentLat;

    /** Longitude courante du livreur. */
    double currentLon;
}
