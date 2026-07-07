package com.yowyob.tiibntick.core.billing.templates.application.command;

import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

import java.util.Map;
import java.util.UUID;

/**
 * Command to apply a catalog billing policy template for a specific actor,
 * resulting in the creation of a new {@code BillingPolicy} in {@code tnt-billing-pricing}.
 *
 * <p>The actor provides optional parameter overrides. Any parameter not overridden
 * will use the template's default value. The resulting BillingPolicy is created
 * in DRAFT state — the actor must explicitly activate it.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Value
@Builder
public class ApplyTemplateCommand {

    /**
     * The catalog template code to apply (e.g. {@code TPL-BASE-STD}, {@code TPL-FRAGILE}).
     */
    @NotBlank
    String templateCode;

    /**
     * The UUID of the actor applying the template.
     * References the kernel's actorId — no join to Actor table (integration key only).
     */
    @NotBlank
    String ownerActorId;

    /**
     * The type of the actor applying the template. Used for applicability check.
     */
    @NotNull
    PolicyOwnerType ownerType;

    /**
     * The tenant ID of the actor. Used for multi-tenant BillingPolicy isolation.
     */
    @NotBlank
    String tenantId;

    /**
     * Optional custom name for the generated BillingPolicy.
     * If null, a default name is generated from the template name + actor tradeName.
     */
    String policyName;

    /**
     * Optional map of parameter key → custom value overrides.
     * Parameters not included here will use the template's default value.
     * Example: {@code {"basePrice": "700", "perKmRate": "60"}}
     */
    @Builder.Default
    Map<String, String> customizedParameters = Map.of();

    /**
     * Whether this application should also save a CustomPolicyTemplate
     * for the actor to reuse later.
     */
    @Builder.Default
    boolean saveAsCustomTemplate = false;

    /**
     * The name for the saved CustomPolicyTemplate (only used if {@link #saveAsCustomTemplate} is true).
     */
    String customTemplateName;

    /**
     * If provided, this application re-applies a previously saved CustomPolicyTemplate
     * rather than directly from the global catalog.
     */
    UUID fromCustomTemplateId;
}
