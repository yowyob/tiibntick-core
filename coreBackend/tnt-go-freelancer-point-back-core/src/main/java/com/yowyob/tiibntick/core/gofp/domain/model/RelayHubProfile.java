package com.yowyob.tiibntick.core.gofp.domain.model;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * Objet fusionné représentant un point relais.
 * Combine :
 *   - tnt-geo-core : relay_hubs (localisation, capacité, statut)
 *   - gofp.relay_hub_extensions (véhicule, infos Market)
 *   - gofp.logistics_pricing (politique tarifaire)
 */
@Value
@Builder
public class RelayHubProfile {

    // ── tnt-geo-core.relay_hubs ────────────────────────────────────
    UUID   hubId;
    UUID   tenantId;
    UUID   branchId;
    String nodeId;
    int    capacitySlots;
    int    currentOccupancy;
    String status;

    // ── Extension Market (gofp.relay_hub_extensions) ──────────────
    String plateNumber;
    String color;
    String logisticsType;
    String logisticsClass;
    String logisticImage;
    Double tankCapacity;
    Double luggageMaxCapacity;
    Double rating;
    String addressStreet;
    String addressCity;
    String addressDistrict;
    String addressCountry;
    String shopPhoto;

    // ── Localisation (tnt-geo-core via road_nodes) ─────────────────
    Double latitude;
    Double longitude;

    /** Retourne true si le hub peut encore accepter des colis. */
    public boolean hasAvailableCapacity() {
        return currentOccupancy < capacitySlots;
    }
}
