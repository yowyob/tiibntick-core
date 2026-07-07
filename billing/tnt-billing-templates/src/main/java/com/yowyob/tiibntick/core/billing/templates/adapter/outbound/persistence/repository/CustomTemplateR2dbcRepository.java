package com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.repository;

import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.entity.CustomPolicyTemplateEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@link CustomPolicyTemplateEntity}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public interface CustomTemplateR2dbcRepository
        extends ReactiveCrudRepository<CustomPolicyTemplateEntity, UUID> {

    /**
     * Returns all custom templates owned by a given actor.
     *
     * @param ownerActorId the actor UUID
     * @return Flux of custom template entities
     */
    Flux<CustomPolicyTemplateEntity> findByOwnerActorId(String ownerActorId);

    /**
     * Returns custom templates based on a specific source catalog template code.
     *
     * @param sourceTemplateCode the catalog template code
     * @return Flux of matching custom template entities
     */
    Flux<CustomPolicyTemplateEntity> findBySourceTemplateCode(String sourceTemplateCode);

    /**
     * Checks if a custom template with the given name exists for a given actor.
     *
     * @param ownerActorId the actor UUID
     * @param name         the custom template name
     * @return Mono boolean
     */
    @Query("SELECT COUNT(*) > 0 FROM billing_custom_policy_templates WHERE owner_actor_id = :ownerActorId AND name = :name")
    Mono<Boolean> existsByOwnerActorIdAndName(String ownerActorId, String name);
}
