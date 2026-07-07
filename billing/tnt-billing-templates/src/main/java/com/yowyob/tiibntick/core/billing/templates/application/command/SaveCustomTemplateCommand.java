package com.yowyob.tiibntick.core.billing.templates.application.command;

import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Command to save a customized policy template configuration for future reuse.
 *
 * <p>The actor can snapshot their current parameter overrides as a named personal
 * template, making it easy to recreate the same BillingPolicy later without
 * re-entering all values.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Value
@Builder
public class SaveCustomTemplateCommand {

    /** The actor who is saving this custom template. */
    @NotBlank
    String ownerActorId;

    /** The type of the owning actor. */
    @NotNull
    PolicyOwnerType ownerType;

    /** The tenant ID of the owning actor. */
    @NotBlank
    String tenantId;

    /** User-defined name for this custom template (e.g. "Mon tarif Yaoundé Centre"). */
    @NotBlank
    String name;

    /**
     * The source catalog template code this customization is based on.
     * Null if creating a fully custom template (admin-level feature).
     */
    String sourceTemplateCode;

    /** The customized parameter values to snapshot. */
    @NotNull
    Map<String, String> customizedParameters;
}
