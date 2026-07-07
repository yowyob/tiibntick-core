package com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web.request;

import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * REST request DTO for applying a billing policy template.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Data
@Schema(description = "Request to apply a billing policy template and create a BillingPolicy")
public class ApplyTemplateRequest {

    @NotBlank
    @Schema(description = "Catalog template code (e.g. TPL-BASE-STD)", example = "TPL-FRAGILE")
    private String templateCode;

    @NotBlank
    @Schema(description = "Actor UUID (kernel integration key)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String ownerActorId;

    @NotNull
    @Schema(description = "Type of the requesting actor", example = "FREELANCER_ORG")
    private PolicyOwnerType ownerType;

    @NotBlank
    @Schema(description = "Tenant ID of the actor", example = "FRL-550e8400-e29b-41d4-a716-446655440000")
    private String tenantId;

    @Schema(description = "Optional custom name for the generated BillingPolicy")
    private String policyName;

    @Schema(description = "Optional custom parameter overrides (key → value string)",
            example = "{\"basePrice\": \"700\", \"perKmRate\": \"60\"}")
    private Map<String, String> customizedParameters;

    @Schema(description = "If true, save the customized parameters as a personal template for reuse")
    private boolean saveAsCustomTemplate = false;

    @Schema(description = "Name for the saved custom template (only used if saveAsCustomTemplate=true)")
    private String customTemplateName;

    @Schema(description = "UUID of a previously saved custom template to re-apply (overrides templateCode)")
    private UUID fromCustomTemplateId;
}
