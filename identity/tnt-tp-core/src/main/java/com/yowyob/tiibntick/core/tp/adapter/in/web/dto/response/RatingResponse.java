package com.yowyob.tiibntick.core.tp.adapter.in.web.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for ThirdPartyRating.
 *
 * @author MANFOUO Braun
 */
public record RatingResponse(
        UUID id,
        UUID ratedThirdPartyId,
        UUID raterActorId,
        String missionId,
        double score,
        String comment,
        Instant createdAt
) {}
