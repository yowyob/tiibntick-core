package com.yowyob.tiibntick.core.agency.compliance.application.port.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Emits a client claim notification to the platform compliance topic
 * ({@code tnt.agency.claims.submitted}).
 */
public interface ClaimEventPublisherPort {

    /**
     * @return the generated claim reference (e.g. {@code CLM-A1B2C3D4}).
     */
    Mono<String> submitClaim(ClaimSubmission submission);

    record ClaimSubmission(
            UUID tenantId,
            UUID agencyId,
            UUID missionId,
            String claimType,
            String description,
            String contactEmail
    ) {}
}
