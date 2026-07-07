package com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * REST response DTO for a personal custom policy template.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Value
@Builder
@Schema(description = "Personal custom billing policy template saved by an actor")
public class CustomPolicyTemplateResponse {

    @Schema(description = "Custom template UUID")
    UUID id;

    @Schema(description = "Actor UUID who owns this template")
    String ownerActorId;

    @Schema(description = "Actor type", example = "FREELANCER_ORG")
    String ownerType;

    @Schema(description = "Display name", example = "Mon tarif Bastos-Nlongkak")
    String name;

    @Schema(description = "Source catalog template code", example = "TPL-BASE-STD")
    String sourceTemplateCode;

    @Schema(description = "UUID of the last generated BillingPolicy")
    UUID lastGeneratedPolicyId;

    @Schema(description = "Saved parameter values")
    Map<String, String> customizedParameters;

    @Schema(description = "Creation timestamp")
    Instant createdAt;

    @Schema(description = "Last update timestamp")
    Instant updatedAt;
}
