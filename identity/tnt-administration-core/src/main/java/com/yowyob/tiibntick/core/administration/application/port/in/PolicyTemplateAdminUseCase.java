package com.yowyob.tiibntick.core.administration.application.port.in;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Inbound port for administrative management of billing policy templates.
 *
 * <p>Platform admins can create, activate, deactivate, and update the defaults
 * of billing policy templates in the {@code tnt-billing-templates} catalog.
 *
 * <p>Operations communicate with tnt-billing-templates via Kafka events or REST.
 *
 * @author MANFOUO Braun
 */
public interface PolicyTemplateAdminUseCase {

    /**
     * Creates a new billing policy template in the catalog.
     * Requires permission: {@code administration:billing-templates:write}.
     *
     * @param cmd creation command
     * @return the template code of the created template
     */
    Mono<String> createTemplate(CreatePolicyTemplateAdminCmd cmd);

    /**
     * Updates the default parameter values for an existing template.
     * Emits {@code PolicyTemplateDefaultsUpdated} Kafka event.
     *
     * @param templateCode the unique template code (e.g., "TPL-FRAGILE")
     * @param newDefaults  map of parameter key → new default value
     * @return Mono completing when the update event is published
     */
    Mono<Void> updateTemplateDefaults(String templateCode, Map<String, Object> newDefaults);

    /**
     * Activates a billing policy template, making it available in the catalog.
     *
     * @param templateCode the template code to activate
     * @return Mono completing when activated
     */
    Mono<Void> activateTemplate(String templateCode);

    /**
     * Deactivates a billing policy template, hiding it from the catalog.
     * Existing policies based on this template are NOT affected.
     *
     * @param templateCode the template code to deactivate
     * @return Mono completing when deactivated
     */
    Mono<Void> deactivateTemplate(String templateCode);

    /**
     * Returns all billing policy templates (active and inactive).
     * Used by admin dashboards for template catalog management.
     *
     * @return stream of template summaries
     */
    Flux<PolicyTemplateSummary> listAllTemplates();

    /**
     * Admin command to create a new policy template.
     */
    record CreatePolicyTemplateAdminCmd(
            String templateCode,
            String name,
            String category,
            String description,
            Map<String, Object> defaultParameters,
            boolean isActive
    ) {}

    /**
     * Summary of a policy template for admin listing.
     */
    record PolicyTemplateSummary(
            String templateCode,
            String name,
            String category,
            boolean isActive,
            int usageCount
    ) {}
}
