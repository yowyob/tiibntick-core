package com.yowyob.tiibntick.core.tp.application.port.in.command;

import java.util.UUID;

/**
 * Command to rate a third party after a delivery.
 *
 * @author MANFOUO Braun
 */
public record RateThirdPartyCommand(
        UUID tenantId,
        UUID ratedThirdPartyId,
        UUID raterActorId,
        String deliveryId,
        double score,
        String comment,
        String ratingContext,
        String subjectKind
) {
    /** Rating without context nor rated-party kind (original form). */
    public RateThirdPartyCommand(UUID tenantId, UUID ratedThirdPartyId, UUID raterActorId,
                                 String deliveryId, double score, String comment) {
        this(tenantId, ratedThirdPartyId, raterActorId, deliveryId, score, comment, null, null);
    }

    public RateThirdPartyCommand {
        if (score < 1.0 || score > 5.0) {
            throw new IllegalArgumentException("Score must be between 1.0 and 5.0");
        }
    }
}
