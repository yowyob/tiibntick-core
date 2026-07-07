package com.yowyob.tiibntick.core.billing.templates.port.inbound;

import com.yowyob.tiibntick.core.billing.templates.application.command.ApplyTemplateCommand;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port for applying a billing policy template.
 *
 * <p>Applying a template creates a new {@code BillingPolicy} in the
 * {@code tnt-billing-pricing} module. The use case coordinates:
 * <ol>
 *   <li>Template lookup and existence check</li>
 *   <li>Applicability check (ownerType in template's applicableTo list)</li>
 *   <li>Inactivity check (template must be active)</li>
 *   <li>Parameter validation (custom values within bounds)</li>
 *   <li>DSL rule generation from template + custom params</li>
 *   <li>BillingPolicy creation via outbound port to tnt-billing-pricing</li>
 *   <li>Optional: save as CustomPolicyTemplate</li>
 *   <li>Event publication (TemplateAppliedEvent)</li>
 * </ol>
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public interface IApplyTemplateUseCase {

    /**
     * Applies a template and creates a BillingPolicy for the actor.
     *
     * @param command the apply template command
     * @return Mono containing the UUID of the newly created BillingPolicy
     * @throws com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateNotFoundException
     *         if the template code does not exist
     * @throws com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateNotApplicableException
     *         if the template is not applicable to the actor's type
     * @throws com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateInactiveException
     *         if the template has been deactivated by admin
     * @throws com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateParameterValidationException
     *         if any customized parameter fails validation
     */
    Mono<UUID> apply(ApplyTemplateCommand command);
}
