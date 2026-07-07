package com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence;

import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.mapper.PolicyTemplateMapper;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.repository.CustomTemplateR2dbcRepository;
import com.yowyob.tiibntick.core.billing.templates.domain.model.CustomPolicyTemplate;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.ICustomTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Adapter implementing {@link ICustomTemplateRepository} using Spring Data R2DBC.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomTemplateRepositoryAdapter implements ICustomTemplateRepository {

    private final CustomTemplateR2dbcRepository customRepo;
    private final PolicyTemplateMapper mapper;

    @Override
    public Mono<CustomPolicyTemplate> save(CustomPolicyTemplate template) {
        var entity = mapper.customToEntity(template);
        return customRepo.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return customRepo.save(entity);
                })
                .map(mapper::customToDomain);
    }

    @Override
    public Mono<CustomPolicyTemplate> findById(UUID id) {
        return customRepo.findById(id).map(mapper::customToDomain);
    }

    @Override
    public Flux<CustomPolicyTemplate> findByOwnerActorId(String ownerActorId) {
        return customRepo.findByOwnerActorId(ownerActorId).map(mapper::customToDomain);
    }

    @Override
    public Flux<CustomPolicyTemplate> findBySourceTemplateCode(String sourceTemplateCode) {
        return customRepo.findBySourceTemplateCode(sourceTemplateCode).map(mapper::customToDomain);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return customRepo.deleteById(id);
    }

    @Override
    public Mono<Boolean> existsByOwnerAndName(String ownerActorId, String name) {
        return customRepo.existsByOwnerActorIdAndName(ownerActorId, name);
    }
}
