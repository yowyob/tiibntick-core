package com.yowyob.tiibntick.core.billing.templates.application.service;

import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Application service that generates a DSL rule string from a template and
 * the actor's effective parameter values (defaults merged with custom overrides).
 *
 * <p>The generated DSL string is then passed to {@code tnt-billing-dsl} for
 * validation and to {@code tnt-billing-pricing} for BillingPolicy creation.
 *
 * <p>Each known template code has a specific DSL generation strategy. Unknown
 * templates fall back to the template's stored {@code defaultDslRules} with
 * parameter placeholders replaced by the effective values.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@Service
public class DslRuleGeneratorService {

    /**
     * Generates the DSL rule string for the given template and effective parameters.
     *
     * @param template           the source template
     * @param effectiveParams    the merged parameter map (defaults + custom overrides)
     * @return the generated DSL rule string
     */
    public String generateDsl(PolicyTemplate template, Map<String, String> effectiveParams) {
        log.debug("Generating DSL for template {} with params {}", template.getTemplateCode(), effectiveParams);

        String dsl = generateByTemplateCode(template.getTemplateCode(), effectiveParams);

        if (dsl == null) {
            // Fallback: replace ${paramKey} placeholders in the stored default rules
            dsl = replacePlaceholders(template.getDefaultDslRules(), effectiveParams);
        }

        log.debug("Generated DSL for template {}: {}", template.getTemplateCode(), dsl);
        return dsl;
    }

    // ─── Template-specific DSL generators ─────────────────────────────────

    private String generateByTemplateCode(String code, Map<String, String> p) {
        return switch (code) {
            case "TPL-BASE-STD"         -> generateBaseStd(p);
            case "TPL-BASE-ZONE"        -> generateBaseZone(p);
            case "TPL-FRAGILE"          -> generateFragile(p);
            case "TPL-PERISHABLE"       -> generatePerishable(p);
            case "TPL-EXPRESS"          -> generateExpress(p);
            case "TPL-BULK"             -> generateBulk(p);
            case "TPL-INTER_CITY"       -> generateInterCity(p);
            case "TPL-LOYALTY_PROGRESSIVE" -> generateLoyaltyProgressive(p);
            case "TPL-VOLUME_SLAB"      -> generateVolumeSlab(p);
            case "TPL-TIME_SLOTS"       -> generateTimeSlots(p);
            case "TPL-WEATHER_ADAPTIVE" -> generateWeatherAdaptive(p);
            case "TPL-HUB_STORAGE"      -> generateHubStorage(p);
            case "TPL-NETWORK_TRANSIT"  -> generateNetworkTransit(p);
            case "TPL-MARKETPLACE_COMMISSION" -> generateMarketplaceCommission(p);
            case "TPL-COMMISSION_DELIVERER"   -> generateCommissionDeliverer(p);
            default -> null;
        };
    }

    private String generateBaseStd(Map<String, String> p) {
        return String.format("""
                // TPL-BASE-STD: Standard Base Pricing
                IF weight >= 0 AND distance >= 0
                  THEN SET_BASE(%s XAF) + ADD_KM(%s XAF) + ADD_KG(%s XAF)
                  MIN(%s XAF)
                """,
                get(p, "basePrice", "500"),
                get(p, "perKmRate", "50"),
                get(p, "perKgRate", "30"),
                get(p, "minPrice", "500"));
    }

    private String generateBaseZone(Map<String, String> p) {
        return String.format("""
                // TPL-BASE-ZONE: Zone-Based Pricing
                IF deliveryZoneType == URBAN THEN SET_BASE(%s XAF) + ADD_KM(%s XAF)
                IF deliveryZoneType == PERI_URBAN THEN SET_BASE(%s XAF) + ADD_KM(%s XAF)
                IF deliveryZoneType == RURAL THEN SET_BASE(%s XAF) + ADD_KM(%s XAF)
                IF deliveryZoneType == DIPLOMATIC THEN SET_BASE(%s XAF) + ADD_KM(%s XAF)
                """,
                get(p, "zoneA_basePrice", "500"), get(p, "perKmRate", "50"),
                get(p, "zoneB_basePrice", "800"), get(p, "perKmRate", "50"),
                get(p, "zoneC_basePrice", "1200"), get(p, "perKmRate", "50"),
                get(p, "zoneD_basePrice", "2000"), get(p, "perKmRate", "50"));
    }

    private String generateFragile(Map<String, String> p) {
        return String.format("""
                // TPL-FRAGILE: Fragile & Precious Specialist
                IF weight >= 0 AND distance >= 0
                  THEN SET_BASE(%s XAF) + ADD_KM(%s XAF)
                IF packageType == FRAGILE THEN ADD_PCT(%s)
                IF packageType == LUXURY THEN ADD_FIXED(%s XAF) + ADD_PCT(%s)
                """,
                get(p, "basePrice", "800"),
                get(p, "perKmRate", "60"),
                get(p, "fragile_surcharge_pct", "20"),
                get(p, "precious_surcharge_fixed", "500"),
                get(p, "luxury_surcharge_pct", "30"));
    }

    private String generatePerishable(Map<String, String> p) {
        return String.format("""
                // TPL-PERISHABLE: Perishable & Refrigerated Delivery
                IF weight >= 0 AND distance >= 0
                  THEN SET_BASE(%s XAF) + ADD_KM(%s XAF)
                IF packageType == PERISHABLE OR packageType == REFRIGERATED
                  THEN ADD_FIXED(%s XAF)
                IF packageType == PHARMACEUTICAL THEN ADD_FIXED(%s XAF)
                """,
                get(p, "basePrice", "1000"),
                get(p, "perKmRate", "80"),
                get(p, "perishable_surcharge_fixed", "700"),
                get(p, "pharmaceutical_surcharge", "500"));
    }

    private String generateExpress(Map<String, String> p) {
        return String.format("""
                // TPL-EXPRESS: Express & Urgent Delivery
                IF weight >= 0 AND distance >= 0
                  THEN SET_BASE(%s XAF) + ADD_KM(%s XAF)
                IF priority == EXPRESS THEN ADD_PCT(%s)
                IF priority == URGENT THEN ADD_PCT(%s)
                IF timeOfDay TIME_IS_BETWEEN 22:00 AND 06:00 THEN ADD_FIXED(%s XAF)
                IF dayOfWeek DAY_IS SATURDAY OR dayOfWeek DAY_IS SUNDAY THEN ADD_FIXED(%s XAF)
                """,
                get(p, "basePrice", "700"),
                get(p, "perKmRate", "70"),
                get(p, "express_2h_surcharge_pct", "50"),
                get(p, "express_1h_surcharge_pct", "100"),
                get(p, "night_surcharge_fixed", "500"),
                get(p, "weekend_surcharge_fixed", "300"));
    }

    private String generateBulk(Map<String, String> p) {
        return String.format("""
                // TPL-BULK: Bulk & High Volume Pricing
                IF weight >= 0 AND distance >= 0
                  THEN SET_BASE(%s XAF) + ADD_KM(%s XAF) + ADD_KG(%s XAF)
                IF weight >= %s THEN DISCOUNT_PCT(%s)
                IF packageCount > 5 THEN ADD_FIXED(-%s XAF) PER_EXTRA_UNIT_ABOVE(5)
                """,
                get(p, "basePrice", "1500"),
                get(p, "perKmRate", "100"),
                get(p, "perKgRate", "20"),
                get(p, "bulk_threshold_kg", "20"),
                get(p, "bulk_discount_pct", "15"),
                get(p, "multi_parcel_discount_per_unit", "50"));
    }

    private String generateInterCity(Map<String, String> p) {
        return String.format("""
                // TPL-INTER_CITY: Inter-City Delivery
                IF distance >= 50
                  THEN SET_BASE(%s XAF) + ADD_KM(%s XAF)
                ADD_FIXED(%s XAF)  // toll allowance
                ADD_FIXED(%s XAF)  // fuel supplement
                """,
                get(p, "basePrice_intercity", "3000"),
                get(p, "perKmRate_intercity", "40"),
                get(p, "toll_allowance", "500"),
                get(p, "fuel_supplement_long_dist", "1000"));
    }

    private String generateLoyaltyProgressive(Map<String, String> p) {
        return String.format("""
                // TPL-LOYALTY_PROGRESSIVE: Progressive Loyalty Discounts
                IF clientTxCount >= %s AND clientTxCount < %s THEN DISCOUNT_PCT(%s)
                IF clientTxCount >= %s AND clientTxCount < %s THEN DISCOUNT_PCT(%s)
                IF clientTxCount >= %s AND clientTxCount < %s THEN DISCOUNT_PCT(%s)
                IF clientTxCount >= %s THEN DISCOUNT_PCT(%s)
                """,
                get(p, "bronze_min_tx", "3"), get(p, "silver_min_tx", "10"), get(p, "bronze_discount_pct", "3"),
                get(p, "silver_min_tx", "10"), get(p, "gold_min_tx", "20"), get(p, "silver_discount_pct", "7"),
                get(p, "gold_min_tx", "20"), get(p, "platinum_min_tx", "50"), get(p, "gold_discount_pct", "12"),
                get(p, "platinum_min_tx", "50"), get(p, "platinum_discount_pct", "20"));
    }

    private String generateVolumeSlab(Map<String, String> p) {
        return String.format("""
                // TPL-VOLUME_SLAB: Volume Slab Discounts (B2B)
                IF monthlyVolume >= %s AND monthlyVolume < %s THEN DISCOUNT_PCT(%s)
                IF monthlyVolume >= %s AND monthlyVolume < %s THEN DISCOUNT_PCT(%s)
                IF monthlyVolume >= %s AND monthlyVolume < %s THEN DISCOUNT_PCT(%s)
                IF monthlyVolume >= %s THEN DISCOUNT_PCT(%s)
                """,
                get(p, "slab1_min", "50000"), get(p, "slab2_min", "150000"), get(p, "slab1_discount_pct", "5"),
                get(p, "slab2_min", "150000"), get(p, "slab3_min", "500000"), get(p, "slab2_discount_pct", "10"),
                get(p, "slab3_min", "500000"), get(p, "slab4_min", "2000000"), get(p, "slab3_discount_pct", "15"),
                get(p, "slab4_min", "2000000"), get(p, "slab4_discount_pct", "20"));
    }

    private String generateTimeSlots(Map<String, String> p) {
        return String.format("""
                // TPL-TIME_SLOTS: Time-Slot Based Pricing
                IF timeOfDay TIME_IS_BETWEEN 00:00 AND 06:00 THEN MULTIPLY(%s)
                IF timeOfDay TIME_IS_BETWEEN 06:00 AND 09:00 THEN MULTIPLY(%s)
                IF timeOfDay TIME_IS_BETWEEN 17:00 AND 20:00 THEN MULTIPLY(%s)
                IF timeOfDay TIME_IS_BETWEEN 20:00 AND 00:00 THEN MULTIPLY(%s)
                IF dayOfWeek DAY_IS_WEEKEND THEN MULTIPLY(%s)
                IF isPublicHoliday == true THEN MULTIPLY(%s)
                """,
                get(p, "night_multiplier", "1.5"),
                get(p, "morning_rush_multiplier", "1.2"),
                get(p, "evening_rush_multiplier", "1.3"),
                get(p, "late_evening_multiplier", "1.2"),
                get(p, "weekend_multiplier", "1.15"),
                get(p, "holiday_multiplier", "1.5"));
    }

    private String generateWeatherAdaptive(Map<String, String> p) {
        return String.format("""
                // TPL-WEATHER_ADAPTIVE: Weather-Adaptive Surcharges
                IF weatherCondition == RAIN_LIGHT THEN ADD_FIXED(%s XAF)
                IF weatherCondition == RAIN_HEAVY THEN ADD_FIXED(%s XAF)
                IF weatherCondition == FLOOD THEN ADD_FIXED(%s XAF)
                IF weatherCondition == STORM THEN ADD_FIXED(%s XAF)
                IF weatherCondition == HEAT_EXTREME THEN ADD_FIXED(%s XAF)
                """,
                get(p, "rain_light_surcharge", "200"),
                get(p, "rain_heavy_surcharge", "500"),
                get(p, "flood_surcharge", "1000"),
                get(p, "storm_surcharge", "800"),
                get(p, "heat_surcharge", "300"));
    }

    private String generateHubStorage(Map<String, String> p) {
        return String.format("""
                // TPL-HUB_STORAGE: Hub Point Relay Storage Fees
                IF storageHours <= %s THEN SET_BASE(0 XAF)
                IF storageHours > %s AND storageHours <= 48 THEN ADD_PER_INTERVAL(%s XAF, 12h)
                IF storageHours > 48 AND storageHours <= 72 THEN ADD_PER_INTERVAL(%s XAF, 12h)
                IF storageHours > 72 THEN ADD_PER_INTERVAL(%s XAF, 12h)
                IF packageType CONTAINS HAZARDOUS_DECLARED THEN MULTIPLY(%s)
                IF requiresRefrigeration == true THEN ADD_PER_INTERVAL(%s XAF, 12h)
                """,
                get(p, "free_storage_hours", "24"),
                get(p, "free_storage_hours", "24"),
                get(p, "storage_fee_per_12h_slab2", "100"),
                get(p, "storage_fee_per_12h_slab3", "200"),
                get(p, "storage_fee_per_12h_slab4", "500"),
                get(p, "hazardous_storage_multiplier", "3"),
                get(p, "refrigerated_storage_extra", "300"));
    }

    private String generateNetworkTransit(Map<String, String> p) {
        return String.format("""
                // TPL-NETWORK_TRANSIT: Inter-Node Network Transit Fees
                SET_BASE(%s XAF)
                ADD_PER_HOP(%s XAF)
                IF isInterCity == true THEN ADD_FIXED(%s XAF)
                IF priority == PRIORITY THEN ADD_FIXED(%s XAF)
                """,
                get(p, "node_handling_fee", "200"),
                get(p, "per_hop_fee", "150"),
                get(p, "inter_city_transit_fee", "500"),
                get(p, "priority_network_surcharge", "300"));
    }

    private String generateMarketplaceCommission(Map<String, String> p) {
        return String.format("""
                // TPL-MARKETPLACE_COMMISSION: Platform Marketplace Commission
                PLATFORM_COMMISSION(%s%%)
                ADD_FIXED(%s XAF)  // payment processing fee
                """,
                get(p, "platform_commission_pct", "5"),
                get(p, "payment_processing_fee", "100"));
    }

    private String generateCommissionDeliverer(Map<String, String> p) {
        return String.format("""
                // TPL-COMMISSION_DELIVERER: Deliverer Remuneration Model
                DELIVERER_BASE_COMMISSION(%s%%)
                IF deliveredOnTime == true THEN DELIVERER_BONUS_PCT(%s)
                IF deliveredLate == true AND lateMinutes > 30 THEN DELIVERER_PENALTY_PCT(%s)
                IF delivererRating >= %s THEN DELIVERER_BONUS_PCT(%s)
                DELIVERER_MINIMUM_COMMISSION(%s XAF)
                """,
                get(p, "base_commission_pct", "60"),
                get(p, "bonus_ontime_pct", "10"),
                get(p, "penalty_late_pct", "5"),
                get(p, "bonus_rating_threshold", "4.5"),
                get(p, "bonus_rating_pct", "5"),
                get(p, "minimum_commission", "300"));
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    /**
     * Gets a value from the parameters map, returning the fallback if not present.
     */
    private String get(Map<String, String> params, String key, String fallback) {
        return params.getOrDefault(key, fallback);
    }

    /**
     * Replaces ${key} placeholder patterns in a DSL template string with actual values.
     */
    private String replacePlaceholders(String dslTemplate, Map<String, String> params) {
        if (dslTemplate == null) return "";
        String result = dslTemplate;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
