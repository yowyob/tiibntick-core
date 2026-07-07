package com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

/**
 * REST response DTO for a single template parameter.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Value
@Builder
@Schema(description = "Single adjustable parameter of a billing policy template")
public class TemplateParameterResponse {

    @Schema(description = "Technical key", example = "basePrice")
    String key;

    @Schema(description = "French label", example = "Prix de base de la livraison")
    String labelFr;

    @Schema(description = "English label", example = "Base delivery price")
    String labelEn;

    @Schema(description = "Default value", example = "500")
    String defaultValue;

    @Schema(description = "Minimum allowed value", example = "100")
    String minValue;

    @Schema(description = "Maximum allowed value", example = "50000")
    String maxValue;

    @Schema(description = "Unit displayed", example = "XAF")
    String unit;

    @Schema(description = "Parameter type", example = "MONEY")
    String type;

    @Schema(description = "Help text shown as tooltip")
    String helpText;
}
