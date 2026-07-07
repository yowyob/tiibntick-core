package com.yowyob.tiibntick.core.billing.templates.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable result of a template price preview calculation.
 *
 * <p>Returned by {@code PreviewPriceFromTemplateUseCase} to give the actor a
 * concrete price estimate before committing to applying the template as a
 * BillingPolicy. The result includes the breakdown of individual components
 * so the actor can understand exactly how the price was computed.
 *
 * <p><b>Example for TPL-FRAGILE, 3 kg, 8 km, FRAGILE package:</b>
 * <pre>
 *   basePrice       = 800 XAF
 *   distanceCost    = 8 × 60 = 480 XAF
 *   weightCost      = 0 XAF (no perKgRate in TPL-FRAGILE)
 *   fragileSurcharge = 15% × (800 + 480) = 192 XAF
 *   totalPrice      = 1472 XAF
 * </pre>
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Value
@Builder
public class TemplatePreviewResult {

    /** The template code that was used for this preview. */
    String templateCode;

    /** The computed total price estimate (in XAF). */
    BigDecimal totalPriceXaf;

    /** Breakdown of the base price component. */
    BigDecimal basePriceXaf;

    /** Breakdown of the distance-based cost component. */
    BigDecimal distanceCostXaf;

    /** Breakdown of the weight-based cost component. */
    BigDecimal weightCostXaf;

    /** List of applied surcharges with individual amounts. */
    List<SurchargeBreakdownItem> appliedSurcharges;

    /** Total surcharge amount (sum of all surcharge items). */
    BigDecimal totalSurchargesXaf;

    /** Currency code (always XAF in Cameroon context). */
    @Builder.Default
    String currency = "XAF";

    /** The sample scenario used for this preview (for display purposes). */
    PreviewScenario scenario;

    /** Whether the calculated price is above the configured minimum price. */
    boolean aboveMinimumPrice;

    /** The minimum price that would be applied if the calculated price is too low. */
    BigDecimal minimumPriceXaf;

    /**
     * Single surcharge breakdown line item shown in the preview result.
     */
    @Value
    @Builder
    public static class SurchargeBreakdownItem {
        /** Surcharge code (e.g. FRAGILE, PERISHABLE_REFRIGERATED). */
        String code;
        /** Human-readable label (French). */
        String labelFr;
        /** Human-readable label (English). */
        String labelEn;
        /** Amount contributed by this surcharge. */
        BigDecimal amountXaf;
        /** Display unit (XAF or %). */
        String unit;
    }

    /**
     * The sample pricing scenario provided in the preview request.
     * Stored here so the client can confirm the scenario used.
     */
    @Value
    @Builder
    public static class PreviewScenario {
        double distanceKm;
        double weightKg;
        String packageType;
        String priority;
        int clientTransactionCount;
        String deliveryZoneType;
        String weatherCondition;
        String paymentMethod;
        boolean requiresRefrigeration;
        boolean requiresAssembly;
        boolean requiresIDCheck;
        String timeOfDay;
        String dayOfWeek;
    }
}
