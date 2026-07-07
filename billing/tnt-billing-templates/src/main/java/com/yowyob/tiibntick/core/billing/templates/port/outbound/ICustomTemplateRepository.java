package com.yowyob.tiibntick.core.billing.templates.port.outbound;

import com.yowyob.tiibntick.core.billing.templates.domain.model.CustomPolicyTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for {@link CustomPolicyTemplate} persistence.
 *
 * <p>Custom templates are private to each actor and are not visible
 * in the global catalog. This repository handles all CRUD operations for them.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public interface ICustomTemplateRepository {

    /**
     * Persists a new or updated {@link CustomPolicyTemplate}.
     *
     * @param template the custom template to save
     * @return the saved instance
     */
    Mono<CustomPolicyTemplate> save(CustomPolicyTemplate template);

    /**
     * Finds a custom template by its internal UUID.
     *
     * @param id the custom template UUID
     * @return Mono containing the found template, or empty
     */
    Mono<CustomPolicyTemplate> findById(UUID id);

    /**
     * Returns all custom templates owned by a given actor.
     *
     * @param ownerActorId the actor UUID
     * @return Flux of custom templates belonging to this actor
     */
    Flux<CustomPolicyTemplate> findByOwnerActorId(String ownerActorId);

    /**
     * Returns all custom templates based on a specific catalog template code.
     *
     * @param sourceTemplateCode the catalog template code
     * @return Flux of custom templates derived from this catalog template
     */
    Flux<CustomPolicyTemplate> findBySourceTemplateCode(String sourceTemplateCode);

    /**
     * Deletes a custom template by its UUID.
     *
     * @param id the UUID to delete
     * @return Mono&lt;Void&gt; completing when deletion is done
     */
    Mono<Void> deleteById(UUID id);

    /**
     * Checks whether the given actor already has a custom template with the given name.
     * Used to prevent duplicate names within a single actor's collection.
     *
     * @param ownerActorId the actor UUID
     * @param name         the custom template name to check
     * @return Mono&lt;Boolean&gt; true if a duplicate exists
     */
    Mono<Boolean> existsByOwnerAndName(String ownerActorId, String name);
}
