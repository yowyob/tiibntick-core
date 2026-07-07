package com.yowyob.tiibntick.core.realtime.adapter.in.websocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * JSON payload received in a STOMP SEND frame for a GPS position update.
 * Destination: {@code /app/gps-ping}
 *
 * <p> — Added {@link #freelancerOrgId} to enable FreelancerOrg fleet tracking.
 * When present, the GPS ping is additionally broadcast to
 * {@code /topic/fleet/{freelancerOrgId}} for the OWNER's sub-deliverer dashboard.</p>
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GpsPingMessage(
        String delivererId,
        String missionId,
        double latitude,
        double longitude,
        Double altitude,
        double speedKmh,
        double bearing,
        double accuracy,
        Integer batteryLevel,
        long timestamp,

        // ── : FreelancerOrg fleet tracking ────────────────────────────────

        /**
         * UUID of the FreelancerOrg this deliverer belongs to ().
         * When non-null, the ping is broadcast to {@code /topic/fleet/{freelancerOrgId}}
         * for real-time sub-deliverer tracking by the FreelancerOrg OWNER.
         * Null for standard agency deliverers.
         */
        @Schema(description = "FreelancerOrg UUID for fleet tracking. "
                            + "Null for standard agency deliverers.",
                example = "550e8400-e29b-41d4-a716-446655440000")
        String freelancerOrgId
) {}
