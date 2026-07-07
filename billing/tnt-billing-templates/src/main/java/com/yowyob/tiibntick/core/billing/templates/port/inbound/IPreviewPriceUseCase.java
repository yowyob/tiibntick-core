package com.yowyob.tiibntick.core.billing.templates.port.inbound;

import com.yowyob.tiibntick.core.billing.templates.application.command.PreviewPriceCommand;
import com.yowyob.tiibntick.core.billing.templates.domain.model.TemplatePreviewResult;
import reactor.core.publisher.Mono;

/**
 * Inbound port for computing a price preview from a template without creating any policy.
 *
 * <p>Allows actors to see "Estimated price for 3 kg, 8 km, FRAGILE → 1850 XAF"
 * before committing to applying a template.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public interface IPreviewPriceUseCase {

    /**
     * Computes a price estimate based on a template and a sample scenario.
     *
     * @param command the preview command with template code, parameters, and scenario
     * @return Mono containing the price preview result with full breakdown
     */
    Mono<TemplatePreviewResult> preview(PreviewPriceCommand command);
}
