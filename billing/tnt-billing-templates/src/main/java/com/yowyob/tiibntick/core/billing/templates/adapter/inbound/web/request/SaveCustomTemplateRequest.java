package com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web.request;

import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * REST request DTO for saving a custom billing policy template for reuse.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Data
@Schema(description = "Request to save a personalized template configuration for future reuse")
public class SaveCustomTemplateRequest {

    @NotBlank
    @Schema(description = "Actor UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String ownerActorId;

    @NotNull
    @Schema(description = "Actor type", example = "FREELANCER_ORG")
    private PolicyOwnerType ownerType;

    @NotBlank
    @Schema(description = "Tenant ID", example = "FRL-550e8400-e29b-41d4-a716-446655440000")
    private String tenantId;

    @NotBlank
    @Schema(description = "Display name for this custom template", example = "Mon tarif Bastos-Nlongkak")
    private String name;

    @Schema(description = "Source catalog template code (e.g. TPL-BASE-STD)", example = "TPL-BASE-STD")
    private String sourceTemplateCode;

    @NotNull
    @Schema(description = "Parameter values to snapshot",
            example = "{\"basePrice\": \"700\", \"perKmRate\": \"60\"}")
    private Map<String, String> customizedParameters;
}
