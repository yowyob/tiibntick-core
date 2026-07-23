package com.yowyob.tiibntick.core.trust.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.PolVerificationRecord;

/**
 * Response DTO — Proof-of-Location verification details.
 * Returned by {@code GET /tnt/trust/pol/{actorId}/verifications}.
 *
 * @author MANFOUO Braun
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PolVerificationResponse(
        String eventId,
        String actorId,
        String tenantId,
        double gpsLat,
        double gpsLng,
        String polHash,
        String verifiedAt,
        String blockchainTxHash) {

    /** Converts a {@link PolVerificationRecord} domain object to this DTO. */
    public static PolVerificationResponse from(final PolVerificationRecord verification) {
        return new PolVerificationResponse(
                verification.getEventId(),
                verification.getActorId(),
                verification.getTenantId(),
                verification.getGpsLat(),
                verification.getGpsLng(),
                verification.getPolHash(),
                verification.getVerifiedAt() != null ? verification.getVerifiedAt().toString() : null,
                verification.getBlockchainTxHash());
    }
}
