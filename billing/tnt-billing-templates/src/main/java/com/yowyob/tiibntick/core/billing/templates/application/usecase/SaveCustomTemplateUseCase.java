package com.yowyob.tiibntick.core.billing.templates.application.usecase;

import com.yowyob.tiibntick.core.billing.templates.application.command.SaveCustomTemplateCommand;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplateParameterValidationService;
import com.yowyob.tiibntick.core.billing.templates.domain.event.CustomTemplateSavedEvent;
import com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateNotFoundException;
import com.yowyob.tiibntick.core.billing.templates.domain.model.CustomPolicyTemplate;
import com.yowyob.tiibntick.core.billing.templates.port.inbound.ISaveCustomTemplateUseCase;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.ICustomTemplateRepository;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.IPolicyTemplateRepository;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.ITemplateEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implementation of the save/manage custom template use case.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaveCustomTemplateUseCase implements ISaveCustomTemplateUseCase {

    private final ICustomTemplateRepository customTemplateRepository;
    private final IPolicyTemplateRepository templateRepository;
    private final TemplateParameterValidationService validationService;
    private final ITemplateEventPublisher eventPublisher;

    /**
     * {@inheritDoc}
     *
     * <p>{@code @Transactional} so the custom-template row and the outbox envelope/entry
     * written by {@link ITemplateEventPublisher} (Chantier C · Audit n°3 · P5) commit
     * atomically — the save can no longer succeed while its event is silently lost.
     */
    @Override
    @Transactional
    public Mono<CustomPolicyTemplate> save(SaveCustomTemplateCommand command) {
        log.info("Saving custom template '{}' for actor {}", command.getName(), command.getOwnerActorId());

        // Validate that the source catalog template exists (if provided)
        Mono<Void> catalogCheck = command.getSourceTemplateCode() != null
                ? templateRepository.findByTemplateCode(command.getSourceTemplateCode())
                .switchIfEmpty(Mono.error(new TemplateNotFoundException(command.getSourceTemplateCode())))
                .flatMap(template -> validationService.validate(template, command.getCustomizedParameters()))
                .then()
                : Mono.empty();

        return catalogCheck.then(
                customTemplateRepository.existsByOwnerAndName(command.getOwnerActorId(), command.getName())
                        .flatMap(exists -> {
                            if (exists) {
                                return Mono.error(new IllegalArgumentException(
                                        "A custom template named '" + command.getName()
                                                + "' already exists for actor " + command.getOwnerActorId()));
                            }
                            CustomPolicyTemplate custom = CustomPolicyTemplate.createNew(
                                    command.getOwnerActorId(),
                                    command.getOwnerType(),
                                    command.getName(),
                                    command.getSourceTemplateCode(),
                                    command.getCustomizedParameters()
                            );
                            return customTemplateRepository.save(custom);
                        })
        ).flatMap(saved -> {
            CustomTemplateSavedEvent event = CustomTemplateSavedEvent.builder()
                    .customTemplateId(saved.getId())
                    .customTemplateName(saved.getName())
                    .ownerActorId(saved.getOwnerActorId())
                    .ownerType(saved.getOwnerType())
                    .sourceTemplateCode(saved.getSourceTemplateCode())
                    .tenantId(command.getTenantId())
                    .build();
            return eventPublisher.publishCustomTemplateSaved(event).thenReturn(saved);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flux<CustomPolicyTemplate> listByOwner(String ownerActorId) {
        log.debug("Listing custom templates for actor {}", ownerActorId);
        return customTemplateRepository.findByOwnerActorId(ownerActorId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<Void> delete(UUID customTemplateId, String ownerActorId) {
        log.info("Deleting custom template {} for actor {}", customTemplateId, ownerActorId);
        return customTemplateRepository.findById(customTemplateId)
                .switchIfEmpty(Mono.error(new TemplateNotFoundException(customTemplateId)))
                .flatMap(template -> {
                    if (!template.getOwnerActorId().equals(ownerActorId)) {
                        return Mono.error(new SecurityException(
                                "Actor " + ownerActorId + " is not the owner of custom template " + customTemplateId));
                    }
                    return customTemplateRepository.deleteById(customTemplateId);
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<CustomPolicyTemplate> rename(UUID customTemplateId, String ownerActorId, String newName) {
        log.info("Renaming custom template {} to '{}' for actor {}", customTemplateId, newName, ownerActorId);
        return customTemplateRepository.findById(customTemplateId)
                .switchIfEmpty(Mono.error(new TemplateNotFoundException(customTemplateId)))
                .flatMap(template -> {
                    if (!template.getOwnerActorId().equals(ownerActorId)) {
                        return Mono.error(new SecurityException(
                                "Actor " + ownerActorId + " is not the owner of custom template " + customTemplateId));
                    }
                    return customTemplateRepository.save(template.rename(newName));
                });
    }
}
