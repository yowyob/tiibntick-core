package com.yowyob.tiibntick.core.marketback.application.port.in.command;

import com.yowyob.tiibntick.core.marketback.domain.model.ReviewTag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Command — client submits a review for a provider.
 * @author MANFOUO Braun
 */
public record SubmitReviewCommand(
        @NotNull String tenantId,
        @NotNull UUID orderId,
        @NotNull UUID clientId,
        @NotNull UUID listingId,
        @NotNull UUID providerId,
        @Min(1) @Max(5) int overall,
        @Min(1) @Max(5) int punctuality,
        @Min(1) @Max(5) int communication,
        @Min(1) @Max(5) int packaging,
        @Min(1) @Max(5) int value,
        String comment,
        List<ReviewTag> tags
) {}
