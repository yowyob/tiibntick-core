package com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * REST response DTO returned after successfully applying a billing policy template.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Value
@Builder
@Schema(description = "Result of applying a billing policy template")
public class ApplyTemplateResponse {

    @Schema(description = "UUID of the newly created BillingPolicy (in tnt-billing-pricing)",
            example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    UUID createdPolicyId;

    @Schema(description = "The template code that was applied", example = "TPL-FRAGILE")
    String templateCode;

    @Schema(description = "Display name of the created BillingPolicy",
            example = "Fragile & Precious Specialist — Ernest Pharma Delivery")
    String policyName;

    @Schema(description = "Current status of the created policy (always DRAFT initially)", example = "DRAFT")
    String policyStatus;

    @Schema(description = "Whether a custom template was also saved for future reuse", example = "false")
    boolean customTemplateSaved;

    @Schema(description = "Message for the user",
            example = "BillingPolicy successfully created in DRAFT state. Activate it to start using it.")
    String message;
}
