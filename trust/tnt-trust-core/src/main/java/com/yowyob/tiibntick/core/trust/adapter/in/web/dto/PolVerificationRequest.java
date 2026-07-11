package com.yowyob.tiibntick.core.trust.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request DTO — Record a Proof-of-Location verification.
 * Posted to {@code POST /tnt/trust/pol/record}.
 *
 * <p>The {@code polHash} is computed on the mobile device using the
 * actor's private key and GPS data, then verified server-side by
 * {@code tnt-realtime-core} before being forwarded here for anchoring.
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolVerificationRequest(
        String actorId,
        String tenantId,
        double gpsLat,
        double gpsLng,
        String polHash) {}
