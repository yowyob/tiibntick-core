package com.yowyob.tiibntick.core.billing.templates.application.usecase;

import com.yowyob.tiibntick.core.billing.templates.application.command.PreviewPriceCommand;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplateApplicabilityService;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplateParameterValidationService;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplatePriceCalculatorService;
import com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateNotFoundException;
import com.yowyob.tiibntick.core.billing.templates.domain.model.TemplatePreviewResult;
import com.yowyob.tiibntick.core.billing.templates.port.inbound.IPreviewPriceUseCase;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.IPolicyTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Implementation of the price preview use case.
 *
 * <p>Computes a price estimate by:
 * <ol>
 *   <li>Looking up the template from the catalog</li>
 *   <li>Checking applicability (ownerType)</li>
 *   <li>Validating any custom parameter overrides</li>
 *   <li>Merging overrides with template defaults</li>
 *   <li>Running the local price calculator against the sample scenario</li>
 * </ol>
 *
 * <p>No BillingPolicy is created. No Kafka event is published. This is purely
 * a read-side calculation for UI preview purposes.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewPriceFromTemplateUseCase implements IPreviewPriceUseCase {

    private final IPolicyTemplateRepository templateRepository;
    private final TemplateApplicabilityService applicabilityService;
    private final TemplateParameterValidationService validationService;
    private final TemplatePriceCalculatorService calculatorService;

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<TemplatePreviewResult> preview(PreviewPriceCommand command) {
        log.debug("Computing price preview for template={} ownerType={} distanceKm={} weightKg={}",
                command.getTemplateCode(), command.getOwnerType(),
                command.getDistanceKm(), command.getWeightKg());

        return templateRepository.findByTemplateCode(command.getTemplateCode())
                .switchIfEmpty(Mono.error(new TemplateNotFoundException(command.getTemplateCode())))
                // Check applicability and active status
                .flatMap(template -> applicabilityService.validate(template, command.getOwnerType()))
                // Validate custom parameter overrides
                .flatMap(template -> validationService.validate(template, command.getCustomizedParameters())
                        .thenReturn(template))
                // Compute preview
                .map(template -> {
                    Map<String, String> effectiveParams = validationService.mergeWithDefaults(
                            template, command.getCustomizedParameters());
                    TemplatePreviewResult result = calculatorService.compute(
                            template.getTemplateCode(), effectiveParams, command);
                    log.debug("Preview result for template {}: totalPrice={} XAF",
                            template.getTemplateCode(), result.getTotalPriceXaf());
                    return result;
                });
    }
}
