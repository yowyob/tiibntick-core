package com.yowyob.tiibntick.core.billing.templates.port.inbound;

import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyTemplate;
import com.yowyob.tiibntick.core.billing.templates.domain.model.TemplateCategory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Inbound port for template catalog listing queries.
 *
 * <p>This is the primary read-side interface for browsing the template catalog.
 * It can be called by the REST adapter or by other modules via Spring DI.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public interface IListTemplatesUseCase {

    /**
     * Returns all active templates applicable to the given actor type.
     *
     * @param ownerType the actor type requesting the catalog
     * @return Flux of applicable active templates
     */
    Flux<PolicyTemplate> listForOwnerType(PolicyOwnerType ownerType);

    /**
     * Returns all active templates in the given category for the given actor type.
     *
     * @param ownerType the actor type requesting the catalog
     * @param category  the category to filter by
     * @return Flux of matching templates
     */
    Flux<PolicyTemplate> listByCategory(PolicyOwnerType ownerType, TemplateCategory category);

    /**
     * Returns a single template by its code.
     *
     * @param templateCode the business key (e.g. TPL-BASE-STD)
     * @return Mono containing the template, or error if not found
     */
    Mono<PolicyTemplate> getByCode(String templateCode);

    /**
     * Returns all templates (including inactive) for admin management.
     *
     * @return Flux of all templates
     */
    Flux<PolicyTemplate> listAll();
}
