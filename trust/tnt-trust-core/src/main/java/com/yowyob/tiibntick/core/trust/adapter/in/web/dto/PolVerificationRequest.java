package com.yowyob.tiibntick.core.trust.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

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
        @NotBlank(message = "actorId is required") String actorId,
        String tenantId,
        double gpsLat,
        double gpsLng,
        @NotBlank(message = "polHash is required") String polHash) {}
