package com.yowyob.tiibntick.core.tp.adapter.in.web.dto.request;

import jakarta.validation.constraints.*;

import java.util.UUID;

/**
 * HTTP request body for rating a third party.
 *
 * @author MANFOUO Braun
 */
public record RateThirdPartyRequest(
        @NotNull UUID raterActorId,
        @NotBlank String missionId,
        @Min(1) @Max(5) double score,
        String comment
) {}
