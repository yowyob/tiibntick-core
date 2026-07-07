package com.yowyob.tiibntick.core.billing.templates.application.usecase;

import com.yowyob.tiibntick.core.billing.templates.application.command.ApplyTemplateCommand;
import com.yowyob.tiibntick.core.billing.templates.application.service.DslRuleGeneratorService;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplateApplicabilityService;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplateParameterValidationService;
import com.yowyob.tiibntick.core.billing.templates.domain.event.TemplateAppliedEvent;
import com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateNotFoundException;
import com.yowyob.tiibntick.core.billing.templates.domain.model.CustomPolicyTemplate;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyTemplate;
import com.yowyob.tiibntick.core.billing.templates.port.inbound.IApplyTemplateUseCase;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.IBillingPolicyCreationPort;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.IBillingPolicyCreationPort.BillingPolicyCreationRequest;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.ICustomTemplateRepository;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.IPolicyTemplateRepository;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.ITemplateEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Implementation of the apply-template use case.
 *
 * <p>Orchestrates the full template application flow:
 * <ol>
 *   <li>Resolve the source template (from catalog or from custom template)</li>
 *   <li>Check applicability and activation status</li>
 *   <li>Validate parameter overrides</li>
 *   <li>Merge overrides with defaults</li>
 *   <li>Generate DSL rules</li>
 *   <li>Create BillingPolicy via outbound port to tnt-billing-pricing</li>
 *   <li>Optionally save a CustomPolicyTemplate</li>
 *   <li>Publish TemplateAppliedEvent</li>
 * </ol>
 *
 * <p>All steps are chained reactively using Project Reactor operators. The operation
 * is atomic at the BillingPolicy creation step — if policy creation fails, no event
 * is published and no custom template is saved.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyTemplateUseCase implements IApplyTemplateUseCase {

    private final IPolicyTemplateRepository templateRepository;
    private final ICustomTemplateRepository customTemplateRepository;
    private final TemplateApplicabilityService applicabilityService;
    private final TemplateParameterValidationService validationService;
    private final DslRuleGeneratorService dslGeneratorService;
    private final IBillingPolicyCreationPort billingPolicyCreationPort;
    private final ITemplateEventPublisher eventPublisher;

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<UUID> apply(ApplyTemplateCommand command) {
        log.info("Applying template {} for actor {} (ownerType={})",
                command.getTemplateCode(), command.getOwnerActorId(), command.getOwnerType());

        return resolveTemplate(command)
                // Step 1: Check applicability and active status
                .flatMap(template -> applicabilityService.validate(template, command.getOwnerType()))
                // Step 2: Validate custom parameters
                .flatMap(template -> validationService.validate(template, command.getCustomizedParameters())
                        .thenReturn(template))
                // Step 3: Merge parameters and generate DSL
                .flatMap(template -> {
                    Map<String, String> effectiveParams = validationService.mergeWithDefaults(
                            template, command.getCustomizedParameters());
                    String generatedDsl = dslGeneratorService.generateDsl(template, effectiveParams);

                    String policyName = command.getPolicyName() != null
                            ? command.getPolicyName()
                            : template.getName() + " — " + command.getOwnerActorId();

                    BillingPolicyCreationRequest request = new BillingPolicyCreationRequest(
                            command.getTenantId(),
                            command.getOwnerActorId(),
                            command.getOwnerType(),
                            policyName,
                            true,
                            template.getTemplateCode(),
                            generatedDsl,
                            effectiveParams
                    );

                    // Step 4: Create BillingPolicy in tnt-billing-pricing
                    return billingPolicyCreationPort.createBillingPolicy(request)
                            .flatMap(policyId -> {
                                log.info("BillingPolicy {} created for actor {} from template {}",
                                        policyId, command.getOwnerActorId(), template.getTemplateCode());

                                // Step 5: Optionally save custom template
                                Mono<Void> saveCustom = command.isSaveAsCustomTemplate()
                                        ? saveCustomTemplate(command, template, effectiveParams)
                                        : Mono.empty();

                                // Step 6: Publish event
                                TemplateAppliedEvent event = TemplateAppliedEvent.builder()
                                        .templateCode(template.getTemplateCode())
                                        .templateName(template.getName())
                                        .ownerActorId(command.getOwnerActorId())
                                        .ownerType(command.getOwnerType())
                                        .createdPolicyId(policyId)
                                        .tenantId(command.getTenantId())
                                        .fromCustomTemplate(command.getFromCustomTemplateId() != null)
                                        .customTemplateId(command.getFromCustomTemplateId())
                                        .appliedParameters(effectiveParams)
                                        .build();

                                return saveCustom
                                        .then(eventPublisher.publishTemplateApplied(event))
                                        .thenReturn(policyId);
                            });
                });
    }

    // ─── Private helpers ───────────────────────────────────────────────────

    /**
     * Resolves the source template — either from the global catalog directly,
     * or from a previously saved custom template that references a catalog template.
     */
    private Mono<PolicyTemplate> resolveTemplate(ApplyTemplateCommand command) {
        if (command.getFromCustomTemplateId() != null) {
            log.debug("Resolving template from custom template id={}", command.getFromCustomTemplateId());
            return customTemplateRepository.findById(command.getFromCustomTemplateId())
                    .switchIfEmpty(Mono.error(new TemplateNotFoundException(command.getFromCustomTemplateId())))
                    .flatMap(custom -> templateRepository.findByTemplateCode(custom.getSourceTemplateCode())
                            .switchIfEmpty(Mono.error(new TemplateNotFoundException(custom.getSourceTemplateCode()))))
                    // Merge custom template's parameters into the command's parameter overrides
                    .doOnNext(template -> log.debug("Resolved catalog template {} from custom template",
                            template.getTemplateCode()));
        }

        return templateRepository.findByTemplateCode(command.getTemplateCode())
                .switchIfEmpty(Mono.error(new TemplateNotFoundException(command.getTemplateCode())));
    }

    /**
     * Saves a CustomPolicyTemplate snapshot for the actor after successful application.
     */
    private Mono<Void> saveCustomTemplate(
            ApplyTemplateCommand command,
            PolicyTemplate template,
            Map<String, String> effectiveParams) {

        String name = command.getCustomTemplateName() != null
                ? command.getCustomTemplateName()
                : "Custom - " + template.getName();

        CustomPolicyTemplate custom = CustomPolicyTemplate.createNew(
                command.getOwnerActorId(),
                command.getOwnerType(),
                name,
                template.getTemplateCode(),
                effectiveParams
        );

        return customTemplateRepository.save(custom)
                .doOnNext(saved -> log.debug("Saved custom template {} for actor {}",
                        saved.getId(), command.getOwnerActorId()))
                .then();
    }
}
