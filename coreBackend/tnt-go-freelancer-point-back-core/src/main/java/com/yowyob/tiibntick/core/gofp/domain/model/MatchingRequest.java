package com.yowyob.tiibntick.core.gofp.domain.model;

import com.yowyob.tiibntick.core.gofp.domain.model.enums.VehicleType;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * Demande de matching d'une annonce vers des livreurs candidats.
 * Paramètres d'entrée du MatchingCoreService.
 */
@Value
@Builder
public class MatchingRequest {

    /** Identifiant de l'annonce à matcher. */
    UUID announcementId;

    /** Latitude du point de collecte. */
    double pickupLat;

    /** Longitude du point de collecte. */
    double pickupLon;

    /** Rayon initial de recherche en km (peut être élargi par MatchingExpansionPolicy). */
    double initialRadiusKm;

    /** Type de véhicule requis par le client (null = tous). */
    VehicleType requiredVehicleType;

    /** Poids du colis en kg (pour filtre capacité). */
    Double packetWeightKg;

    /** Tenant pour le filtre multi-tenant. */
    UUID tenantId;
}
