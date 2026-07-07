package com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.repository;

import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.entity.PolicyTemplateEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@link PolicyTemplateEntity}.
 *
 * <p>Provides basic CRUD operations and custom queries for template retrieval.
 * More complex queries that require joining with {@code billing_template_parameters}
 * or filtering by JSON-embedded {@code applicable_to_json} are handled in
 * {@link PolicyTemplateRepositoryAdapter}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public interface PolicyTemplateR2dbcRepository
        extends ReactiveCrudRepository<PolicyTemplateEntity, UUID> {

    /**
     * Finds a template by its unique business key.
     *
     * @param templateCode the business key (e.g. TPL-BASE-STD)
     * @return Mono of the matching entity
     */
    Mono<PolicyTemplateEntity> findByTemplateCode(String templateCode);

    /**
     * Returns all active templates.
     *
     * @return Flux of active template entities
     */
    Flux<PolicyTemplateEntity> findByActiveTrue();

    /**
     * Returns all active templates in the given category.
     *
     * @param category the category enum name
     * @return Flux of matching active template entities
     */
    Flux<PolicyTemplateEntity> findByActiveTrueAndCategory(String category);

    /**
     * Checks if a template with the given code exists.
     *
     * @param templateCode the code to check
     * @return Mono boolean
     */
    Mono<Boolean> existsByTemplateCode(String templateCode);

    /**
     * Counts the number of active templates.
     *
     * @return Mono count
     */
    @Query("SELECT COUNT(*) FROM billing_policy_templates WHERE is_active = true")
    Mono<Long> countActiveTemplates();
}
