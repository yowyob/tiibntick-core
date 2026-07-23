package com.yowyob.tiibntick.core.trust.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.GeofenceCrossingRecord;

/**
 * Response DTO — Geofence zone crossing details.
 * Returned by {@code GET /tnt/trust/geofence/{actorId}/crossings}.
 *
 * @author MANFOUO Braun
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeofenceCrossingResponse(
        String crossingId,
        String actorId,
        String tenantId,
        String zoneId,
        String zoneName,
        String zoneType,
        String direction,
        double gpsLat,
        double gpsLng,
        String missionId,
        String occurredAt,
        String blockchainTxHash) {

    /** Converts a {@link GeofenceCrossingRecord} domain object to this DTO. */
    public static GeofenceCrossingResponse from(final GeofenceCrossingRecord crossing) {
        return new GeofenceCrossingResponse(
                crossing.getCrossingId(),
                crossing.getActorId(),
                crossing.getTenantId(),
                crossing.getZoneId(),
                crossing.getZoneName(),
                crossing.getZoneType(),
                crossing.getDirection(),
                crossing.getGpsLat(),
                crossing.getGpsLng(),
                crossing.getMissionId(),
                crossing.getOccurredAt() != null ? crossing.getOccurredAt().toString() : null,
                crossing.getBlockchainTxHash());
    }
}
