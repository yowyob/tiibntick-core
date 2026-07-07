package com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence;

import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.entity.PolicyTemplateEntity;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.entity.TemplateParameterEntity;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.mapper.PolicyTemplateMapper;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.repository.PolicyTemplateR2dbcRepository;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.repository.TemplateParameterR2dbcRepository;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyTemplate;
import com.yowyob.tiibntick.core.billing.templates.domain.model.TemplateCategory;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.IPolicyTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Adapter implementing {@link IPolicyTemplateRepository} using Spring Data R2DBC.
 *
 * <p>Because R2DBC does not support JOIN queries natively in the same way JPA does,
 * this adapter manually joins the main template table with the parameters table
 * using separate reactive queries stitched together with {@code flatMap} and
 * {@code collectList}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyTemplateRepositoryAdapter implements IPolicyTemplateRepository {

    private final PolicyTemplateR2dbcRepository templateRepo;
    private final TemplateParameterR2dbcRepository paramRepo;
    private final PolicyTemplateMapper mapper;

    // ─── Save ──────────────────────────────────────────────────────────────

    @Override
    public Mono<PolicyTemplate> save(PolicyTemplate template) {
        PolicyTemplateEntity entity = mapper.toEntity(template);

        return templateRepo.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return templateRepo.save(entity);
                })
                .flatMap(savedEntity -> {
                    List<TemplateParameterEntity> paramEntities = template.getParameters().stream()
                            .map(p -> {
                                TemplateParameterEntity pe = mapper.paramToEntity(p, savedEntity.getId());
                                pe.setNew(true);
                                return pe;
                            })
                            .toList();

                    return paramRepo.deleteByTemplateId(savedEntity.getId())
                            .thenMany(paramRepo.saveAll(paramEntities))
                            .collectList()
                            .map(savedParams -> mapper.toDomain(savedEntity, savedParams));
                });
    }

    // ─── Find ──────────────────────────────────────────────────────────────

    @Override
    public Mono<PolicyTemplate> findById(UUID id) {
        return templateRepo.findById(id)
                .flatMap(this::enrichWithParameters);
    }

    @Override
    public Mono<PolicyTemplate> findByTemplateCode(String templateCode) {
        return templateRepo.findByTemplateCode(templateCode)
                .flatMap(this::enrichWithParameters);
    }

    @Override
    public Flux<PolicyTemplate> findAll() {
        return templateRepo.findAll()
                .flatMap(this::enrichWithParameters);
    }

    @Override
    public Flux<PolicyTemplate> findActiveByOwnerType(PolicyOwnerType ownerType) {
        // Active templates filtered in-memory by applicableTo list
        // (filtering on JSON column is done post-retrieval for compatibility)
        return templateRepo.findByActiveTrue()
                .flatMap(this::enrichWithParameters)
                .filter(t -> t.isApplicableTo(ownerType));
    }

    @Override
    public Flux<PolicyTemplate> findActiveByCategory(TemplateCategory category) {
        return templateRepo.findByActiveTrueAndCategory(category.name())
                .flatMap(this::enrichWithParameters);
    }

    @Override
    public Flux<PolicyTemplate> findActiveByOwnerTypeAndCategory(PolicyOwnerType ownerType, TemplateCategory category) {
        return templateRepo.findByActiveTrueAndCategory(category.name())
                .flatMap(this::enrichWithParameters)
                .filter(t -> t.isApplicableTo(ownerType));
    }

    @Override
    public Mono<Boolean> existsByTemplateCode(String templateCode) {
        return templateRepo.existsByTemplateCode(templateCode);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return paramRepo.deleteByTemplateId(id)
                .then(templateRepo.deleteById(id));
    }

    @Override
    public Mono<Long> countActive() {
        return templateRepo.countActiveTemplates();
    }

    // ─── Helper ────────────────────────────────────────────────────────────

    /**
     * Loads the parameter entities for a given template entity and maps the
     * combined result to a domain {@link PolicyTemplate}.
     *
     * @param entity the template entity to enrich
     * @return Mono of the complete domain object
     */
    private Mono<PolicyTemplate> enrichWithParameters(PolicyTemplateEntity entity) {
        return paramRepo.findByTemplateId(entity.getId())
                .collectList()
                .map(params -> mapper.toDomain(entity, params));
    }
}
