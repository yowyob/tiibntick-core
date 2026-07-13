package com.yowyob.tiibntick.core.marketback.application.port.in.result;

import com.yowyob.tiibntick.core.marketback.domain.model.ReviewStatus;
import com.yowyob.tiibntick.core.marketback.domain.model.ReviewTag;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO — ProviderReview.
 * @author MANFOUO Braun
 */
public record ProviderReviewResponse(
        UUID id,
        UUID listingId,
        UUID orderId,
        UUID clientId,
        UUID providerId,
        ReviewStatus status,
        int overall,
        int punctuality,
        int communication,
        int packaging,
        int value,
        double average,
        String comment,
        List<ReviewTag> tags,
        LocalDateTime createdAt
) {}
