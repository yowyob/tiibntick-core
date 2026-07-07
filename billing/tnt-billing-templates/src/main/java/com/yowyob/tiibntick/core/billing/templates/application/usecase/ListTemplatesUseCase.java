package com.yowyob.tiibntick.core.billing.templates.application.usecase;

import com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateNotFoundException;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyTemplate;
import com.yowyob.tiibntick.core.billing.templates.domain.model.TemplateCategory;
import com.yowyob.tiibntick.core.billing.templates.port.inbound.IListTemplatesUseCase;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.IPolicyTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementation of the template catalog listing use case.
 *
 * <p>Retrieves templates from the repository filtered by actor type and/or category.
 * The list is always pre-filtered to show only active templates (except for admin queries
 * which use {@link #listAll()}).
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListTemplatesUseCase implements IListTemplatesUseCase {

    private final IPolicyTemplateRepository templateRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public Flux<PolicyTemplate> listForOwnerType(PolicyOwnerType ownerType) {
        log.debug("Listing active billing policy templates for ownerType={}", ownerType);
        return templateRepository.findActiveByOwnerType(ownerType)
                .doOnNext(t -> log.trace("Found template {} for ownerType={}", t.getTemplateCode(), ownerType));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flux<PolicyTemplate> listByCategory(PolicyOwnerType ownerType, TemplateCategory category) {
        log.debug("Listing active billing policy templates for ownerType={} and category={}", ownerType, category);
        return templateRepository.findActiveByOwnerTypeAndCategory(ownerType, category);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<PolicyTemplate> getByCode(String templateCode) {
        log.debug("Fetching billing policy template by code={}", templateCode);
        return templateRepository.findByTemplateCode(templateCode)
                .switchIfEmpty(Mono.error(new TemplateNotFoundException(templateCode)));
    }

    /**
     * {@inheritDoc}
     * Admin-only: returns all templates including inactive ones.
     */
    @Override
    public Flux<PolicyTemplate> listAll() {
        log.debug("Listing ALL billing policy templates (admin query)");
        return templateRepository.findAll();
    }
}
