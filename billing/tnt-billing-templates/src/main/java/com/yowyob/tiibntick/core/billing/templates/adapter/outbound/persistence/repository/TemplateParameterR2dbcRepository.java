package com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.repository;

import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.entity.TemplateParameterEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@link TemplateParameterEntity}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public interface TemplateParameterR2dbcRepository
        extends ReactiveCrudRepository<TemplateParameterEntity, UUID> {

    /**
     * Returns all parameters belonging to a given template.
     *
     * @param templateId the parent template UUID
     * @return Flux of parameter entities for this template
     */
    Flux<TemplateParameterEntity> findByTemplateId(UUID templateId);

    /**
     * Deletes all parameters belonging to a given template.
     * Used when a template is fully replaced.
     *
     * @param templateId the parent template UUID
     * @return Mono&lt;Void&gt;
     */
    Mono<Void> deleteByTemplateId(UUID templateId);
}
