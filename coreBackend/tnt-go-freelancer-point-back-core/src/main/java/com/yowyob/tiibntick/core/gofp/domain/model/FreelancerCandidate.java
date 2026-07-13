package com.yowyob.tiibntick.core.gofp.domain.model;

import com.yowyob.tiibntick.core.gofp.domain.model.enums.VehicleType;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * Objet fusionné représentant un livreur candidat au matching.
 * Combine :
 *   - tnt-actor-core : FreelancerProfile (GPS, rating, statut)
 *   - tnt-resource-core : Vehicle (type, capacité)
 *   - gofp.freelancer_extensions (commercial_register, subscription, compteurs)
 *   - gofp.delivery_person_availability (disponibilité courante)
 */
@Value
@Builder
public class FreelancerCandidate {

    // ── Identité (tnt-actor-core) ──────────────────────────────────
    UUID   actorId;
    String firstName;
    String lastName;
    String phone;

    // ── Position GPS courante (tnt-actor-core ou gofp.availability) ──
    double currentLat;
    double currentLon;

    // ── Réputation (tnt-actor-core) ────────────────────────────────
    double ratingScore;
    int    ratingTotal;

    // ── Véhicule (tnt-resource-core) ───────────────────────────────
    VehicleType vehicleType;
    double      capacityKg;
    String      plateNumber;

    // ── Extension Market (gofp.freelancer_extensions) ─────────────
    String  commercialRegister;
    String  commercialName;
    String  taxpayerNumber;
    UUID    subscriptionId;
    int     remainingDeliveries;
    int     failedDeliveries;
    boolean isActive;

    // ── Disponibilité courante (gofp.delivery_person_availability) ──
    boolean isAvailable;
}
