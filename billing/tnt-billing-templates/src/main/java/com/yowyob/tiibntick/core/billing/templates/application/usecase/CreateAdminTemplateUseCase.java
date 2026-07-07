package com.yowyob.tiibntick.core.billing.templates.application.usecase;

import com.yowyob.tiibntick.core.billing.templates.application.command.CreateAdminTemplateCommand;
import com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateNotFoundException;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyTemplate;
import com.yowyob.tiibntick.core.billing.templates.domain.model.TemplateParameter;
import com.yowyob.tiibntick.core.billing.templates.port.inbound.ICreateAdminTemplateUseCase;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.IPolicyTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the admin template management use case.
 *
 * <p>Only TiiBnTick administrators can create, activate, deactivate, or update
 * default parameter values for catalog templates.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateAdminTemplateUseCase implements ICreateAdminTemplateUseCase {

    private final IPolicyTemplateRepository templateRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<PolicyTemplate> create(CreateAdminTemplateCommand command) {
        log.info("Admin {} creating new billing template code={}", command.getAdminActorId(), command.getTemplateCode());

        return templateRepository.existsByTemplateCode(command.getTemplateCode())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException(
                                "A template with code '" + command.getTemplateCode() + "' already exists."));
                    }

                    List<TemplateParameter> parameters = command.getParameters().stream()
                            .map(spec -> TemplateParameter.builder()
                                    .key(spec.getKey())
                                    .labelFr(spec.getLabelFr())
                                    .labelEn(spec.getLabelEn())
                                    .defaultValue(spec.getDefaultValue())
                                    .minValue(spec.getMinValue())
                                    .maxValue(spec.getMaxValue())
                                    .unit(spec.getUnit())
                                    .type(spec.getType())
                                    .helpText(spec.getHelpText())
                                    .build())
                            .toList();

                    PolicyTemplate template = PolicyTemplate.createNew(
                            command.getTemplateCode(),
                            command.getName(),
                            command.getDescription(),
                            command.getCategory(),
                            command.getApplicableTo(),
                            parameters,
                            command.getDefaultDslRules()
                    );

                    return templateRepository.save(template);
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<PolicyTemplate> activate(String templateCode) {
        log.info("Admin activating billing template code={}", templateCode);
        return templateRepository.findByTemplateCode(templateCode)
                .switchIfEmpty(Mono.error(new TemplateNotFoundException(templateCode)))
                .map(PolicyTemplate::activate)
                .flatMap(templateRepository::save);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<PolicyTemplate> deactivate(String templateCode) {
        log.info("Admin deactivating billing template code={}", templateCode);
        return templateRepository.findByTemplateCode(templateCode)
                .switchIfEmpty(Mono.error(new TemplateNotFoundException(templateCode)))
                .map(PolicyTemplate::deactivate)
                .flatMap(templateRepository::save);
    }

    /**
     * {@inheritDoc}
     * Updates the default values of parameters in an existing template.
     * Only the defaultValue field is updated — min/max/type/label are not changed.
     */
    @Override
    public Mono<PolicyTemplate> updateDefaultValues(String templateCode, Map<String, String> newDefaultValues) {
        log.info("Admin updating default values for billing template code={}", templateCode);
        return templateRepository.findByTemplateCode(templateCode)
                .switchIfEmpty(Mono.error(new TemplateNotFoundException(templateCode)))
                .map(template -> {
                    // Rebuild the parameters list with updated defaults
                    List<TemplateParameter> updatedParams = template.getParameters().stream()
                            .map(param -> newDefaultValues.containsKey(param.getKey())
                                    ? TemplateParameter.builder()
                                            .key(param.getKey())
                                            .labelFr(param.getLabelFr())
                                            .labelEn(param.getLabelEn())
                                            .defaultValue(newDefaultValues.get(param.getKey()))
                                            .minValue(param.getMinValue())
                                            .maxValue(param.getMaxValue())
                                            .unit(param.getUnit())
                                            .type(param.getType())
                                            .helpText(param.getHelpText())
                                            .build()
                                    : param)
                            .toList();

                    return PolicyTemplate.builder()
                            .id(template.getId())
                            .templateCode(template.getTemplateCode())
                            .name(template.getName())
                            .description(template.getDescription())
                            .category(template.getCategory())
                            .applicableTo(template.getApplicableTo())
                            .parameters(updatedParams)
                            .defaultDslRules(template.getDefaultDslRules())
                            .active(template.isActive())
                            .createdAt(template.getCreatedAt())
                            .updatedAt(Instant.now())
                            .build();
                })
                .flatMap(templateRepository::save);
    }
}
