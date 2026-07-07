package com.yowyob.tiibntick.core.billing.templates.application.command;

import com.yowyob.tiibntick.core.billing.templates.domain.model.ParameterType;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.templates.domain.model.TemplateCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Command to create a new billing policy template in the global catalog.
 * Restricted to TiiBnTick platform administrators.
 *
 * <p>The admin provides all template metadata, the list of adjustable parameters,
 * and the default DSL rules that will be used when the template is applied.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Value
@Builder
public class CreateAdminTemplateCommand {

    /**
     * Business key for the new template.
     * Must be unique across the catalog. Format: TPL-[CATEGORY]-[VARIANT].
     */
    @NotBlank
    String templateCode;

    /** Display name of the template (English). */
    @NotBlank
    String name;

    /** Detailed description of the template purpose and target scenario. */
    String description;

    /** Category classification for catalog grouping. */
    @NotNull
    TemplateCategory category;

    /** Actor types that can use this template (must not be empty). */
    @NotEmpty
    List<PolicyOwnerType> applicableTo;

    /** List of parameters the actor can customize. */
    @NotNull
    List<ParameterSpec> parameters;

    /**
     * The default DSL rules to be generated. Must be valid according to the
     * DSL level of the target actor types.
     */
    String defaultDslRules;

    /** The admin actor ID who is creating this template. */
    @NotBlank
    String adminActorId;

    /**
     * Specification for a single parameter in the new template.
     */
    @Value
    @Builder
    public static class ParameterSpec {
        @NotBlank
        String key;
        String labelFr;
        String labelEn;
        @NotBlank
        String defaultValue;
        String minValue;
        String maxValue;
        String unit;
        @NotNull
        ParameterType type;
        String helpText;
    }
}
