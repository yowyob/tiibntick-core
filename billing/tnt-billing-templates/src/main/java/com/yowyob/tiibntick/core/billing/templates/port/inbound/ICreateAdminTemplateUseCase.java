package com.yowyob.tiibntick.core.billing.templates.port.inbound;

import com.yowyob.tiibntick.core.billing.templates.application.command.CreateAdminTemplateCommand;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyTemplate;
import reactor.core.publisher.Mono;

/**
 * Inbound port for admin management of the billing policy template catalog.
 * All operations in this port are restricted to TiiBnTick administrators.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public interface ICreateAdminTemplateUseCase {

    /**
     * Creates a new template in the global catalog.
     *
     * @param command the create admin template command
     * @return Mono containing the newly created PolicyTemplate
     */
    Mono<PolicyTemplate> create(CreateAdminTemplateCommand command);

    /**
     * Activates a previously deactivated template, making it available in the catalog.
     *
     * @param templateCode the business key of the template to activate
     * @return Mono containing the updated PolicyTemplate
     */
    Mono<PolicyTemplate> activate(String templateCode);

    /**
     * Deactivates a template, hiding it from the actor catalog.
     * Existing BillingPolicies based on this template are not affected.
     *
     * @param templateCode the business key of the template to deactivate
     * @return Mono containing the updated PolicyTemplate
     */
    Mono<PolicyTemplate> deactivate(String templateCode);

    /**
     * Updates the default parameter values of an existing template.
     * Only changes the defaults — existing BillingPolicies are not retroactively updated.
     *
     * @param templateCode      the business key of the template to update
     * @param newDefaultValues  map of parameterKey → newDefaultValue
     * @return Mono containing the updated PolicyTemplate
     */
    Mono<PolicyTemplate> updateDefaultValues(String templateCode, java.util.Map<String, String> newDefaultValues);
}
