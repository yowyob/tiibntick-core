package com.yowyob.tiibntick.core.billing.templates.port.outbound;

import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyTemplate;
import com.yowyob.tiibntick.core.billing.templates.domain.model.TemplateCategory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port (secondary adapter contract) for {@link PolicyTemplate} persistence.
 *
 * <p>The domain layer depends only on this interface; the actual implementation
 * ({@code PolicyTemplateR2dbcRepository}) lives in the adapter layer and uses Spring
 * Data R2DBC to communicate with PostgreSQL.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public interface IPolicyTemplateRepository {

    /**
     * Persists a new or updated {@link PolicyTemplate}.
     *
     * @param template the template to save
     * @return the saved template (with any DB-generated fields populated)
     */
    Mono<PolicyTemplate> save(PolicyTemplate template);

    /**
     * Finds a template by its internal UUID.
     *
     * @param id the template UUID
     * @return Mono containing the found template, or empty if not found
     */
    Mono<PolicyTemplate> findById(UUID id);

    /**
     * Finds a template by its unique business key ({@code templateCode}).
     *
     * @param templateCode the business key (e.g. TPL-BASE-STD)
     * @return Mono containing the found template, or empty if not found
     */
    Mono<PolicyTemplate> findByTemplateCode(String templateCode);

    /**
     * Returns all templates in the catalog (active and inactive).
     * Used by admin management screens.
     *
     * @return Flux of all templates
     */
    Flux<PolicyTemplate> findAll();

    /**
     * Returns all active templates applicable to the given owner type.
     * Used to filter the catalog shown to an actor.
     *
     * @param ownerType the actor type to filter by
     * @return Flux of active templates applicable to this owner type
     */
    Flux<PolicyTemplate> findActiveByOwnerType(PolicyOwnerType ownerType);

    /**
     * Returns all active templates in the given category.
     *
     * @param category the template category to filter by
     * @return Flux of active templates in this category
     */
    Flux<PolicyTemplate> findActiveByCategory(TemplateCategory category);

    /**
     * Returns all active templates applicable to the given owner type in the given category.
     *
     * @param ownerType the actor type to filter by
     * @param category  the template category to filter by
     * @return Flux of matching templates
     */
    Flux<PolicyTemplate> findActiveByOwnerTypeAndCategory(PolicyOwnerType ownerType, TemplateCategory category);

    /**
     * Checks whether a template with the given code already exists.
     *
     * @param templateCode the code to check
     * @return Mono&lt;Boolean&gt; true if a template with this code exists
     */
    Mono<Boolean> existsByTemplateCode(String templateCode);

    /**
     * Deletes a template by its internal UUID (admin only — used for cleanup).
     *
     * @param id the template UUID to delete
     * @return Mono&lt;Void&gt; completing when deletion is done
     */
    Mono<Void> deleteById(UUID id);

    /**
     * Returns the total count of active templates in the catalog.
     *
     * @return Mono&lt;Long&gt; with the count
     */
    Mono<Long> countActive();
}
