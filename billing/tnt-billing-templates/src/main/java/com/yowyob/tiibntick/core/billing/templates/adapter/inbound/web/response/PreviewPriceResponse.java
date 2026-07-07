package com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST response DTO for the template price preview calculation.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Value
@Builder
@Schema(description = "Price preview result for a billing policy template")
public class PreviewPriceResponse {

    @Schema(description = "Template code used", example = "TPL-FRAGILE")
    String templateCode;

    @Schema(description = "Total estimated price in XAF", example = "1850")
    BigDecimal totalPriceXaf;

    @Schema(description = "Base price component", example = "800")
    BigDecimal basePriceXaf;

    @Schema(description = "Distance-based cost component", example = "480")
    BigDecimal distanceCostXaf;

    @Schema(description = "Weight-based cost component", example = "0")
    BigDecimal weightCostXaf;

    @Schema(description = "Total surcharges applied", example = "192")
    BigDecimal totalSurchargesXaf;

    @Schema(description = "Individual surcharge breakdown")
    List<SurchargeItem> appliedSurcharges;

    @Schema(description = "Currency code", example = "XAF")
    String currency;

    @Schema(description = "Whether the calculated price is above the configured minimum", example = "true")
    boolean aboveMinimumPrice;

    @Schema(description = "Minimum price that would apply if calculated price is too low", example = "500")
    BigDecimal minimumPriceXaf;

    @Schema(description = "The sample scenario used for this preview")
    Map<String, Object> scenario;

    /**
     * Single surcharge item in the breakdown.
     */
    @Value
    @Builder
    public static class SurchargeItem {
        @Schema(description = "Surcharge code", example = "FRAGILE")
        String code;
        @Schema(description = "French label", example = "Colis fragile")
        String labelFr;
        @Schema(description = "English label", example = "Fragile package")
        String labelEn;
        @Schema(description = "Amount in XAF (negative = discount)", example = "192")
        BigDecimal amountXaf;
        @Schema(description = "Unit", example = "%")
        String unit;
    }
}
