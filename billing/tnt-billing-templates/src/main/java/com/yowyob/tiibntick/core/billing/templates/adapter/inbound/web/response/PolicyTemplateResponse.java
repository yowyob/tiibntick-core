package com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST response DTO representing a billing policy template.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Value
@Builder
@Schema(description = "Billing policy template in the catalog")
public class PolicyTemplateResponse {

    @Schema(description = "Internal UUID")
    UUID id;

    @Schema(description = "Unique template code", example = "TPL-FRAGILE")
    String templateCode;

    @Schema(description = "Display name", example = "Fragile & Precious Specialist")
    String name;

    @Schema(description = "Template description")
    String description;

    @Schema(description = "Template category", example = "SPECIALTY")
    String category;

    @Schema(description = "Actor types that can use this template")
    List<String> applicableTo;

    @Schema(description = "Adjustable parameters")
    List<TemplateParameterResponse> parameters;

    @Schema(description = "Default DSL rules (informational)")
    String defaultDslRules;

    @Schema(description = "Whether the template is active", example = "true")
    boolean active;

    @Schema(description = "Creation timestamp")
    Instant createdAt;

    @Schema(description = "Last update timestamp")
    Instant updatedAt;
}
