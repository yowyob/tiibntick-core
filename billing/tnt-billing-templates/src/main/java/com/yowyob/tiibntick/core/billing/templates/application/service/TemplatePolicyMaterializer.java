package com.yowyob.tiibntick.core.billing.templates.application.service;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.CommissionRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.LoyaltyRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PricingRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.SurchargeRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.CommissionAppliesTo;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.SurchargeType;
import com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateNotMaterializableException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a catalog template and its effective parameters into the rules the pricing engine
 * really evaluates ({@link PricingRule} + {@link SurchargeRule}, with conditions written in
 * the DSL grammar), so that what is applied, previewed and billed is one and the same thing.
 *
 * <p>The pseudo-DSL text produced by {@link DslRuleGeneratorService} is display-only: it uses
 * words the DSL parser does not know ({@code ADD_KM}, {@code MIN}, {@code weatherCondition}…)
 * and cannot be evaluated. The rules are therefore built directly here, per template code.
 *
 * <p>Only complete templates (they carry their own base price) are supported. The modifier-only
 * templates (loyalty, time slots, weather) have nothing to price on their own and the hub,
 * network and commission templates belong to other engines; those are refused with
 * {@link TemplateNotMaterializableException}.
 */
@Service
public class TemplatePolicyMaterializer {

    /** Template codes this materializer can turn into engine rules. */
    public static final Set<String> SUPPORTED = Set.of(
            "TPL-BASE-STD", "TPL-BASE-ZONE", "TPL-FRAGILE", "TPL-PERISHABLE",
            "TPL-EXPRESS", "TPL-BULK", "TPL-INTER_CITY",
            "TPL-TIME_SLOTS", "TPL-WEATHER_ADAPTIVE", "TPL-LOYALTY_PROGRESSIVE", "TPL-COMMISSION_DELIVERER");

    /** Templates that only adjust a price: they get the platform's default delivery grid as their base. */
    public static final Set<String> ADJUSTMENT_ONLY = Set.of(
            "TPL-TIME_SLOTS", "TPL-WEATHER_ADAPTIVE", "TPL-LOYALTY_PROGRESSIVE", "TPL-COMMISSION_DELIVERER");

    private static final String CUR = "XAF";

    /** Rules ready to be put in a {@code BillingPolicy}. */
    public record Materialized(List<PricingRule> pricingRules, List<SurchargeRule> surchargeRules,
                               List<LoyaltyRule> loyaltyRules, List<CommissionRule> commissionRules) {
        public Materialized(List<PricingRule> pricingRules, List<SurchargeRule> surchargeRules) {
            this(pricingRules, surchargeRules, List.of(), List.of());
        }
    }

    public boolean supports(String templateCode) {
        return SUPPORTED.contains(templateCode);
    }

    public Materialized materialize(String templateCode, Map<String, String> p) {
        return switch (templateCode) {
            case "TPL-BASE-STD" -> baseStd(p);
            case "TPL-BASE-ZONE" -> baseZone(p);
            case "TPL-FRAGILE" -> fragile(p);
            case "TPL-PERISHABLE" -> perishable(p);
            case "TPL-EXPRESS" -> express(p);
            case "TPL-BULK" -> bulk(p);
            case "TPL-INTER_CITY" -> interCity(p);
            case "TPL-TIME_SLOTS" -> timeSlots(p);
            case "TPL-WEATHER_ADAPTIVE" -> weather(p);
            case "TPL-LOYALTY_PROGRESSIVE" -> loyalty(p);
            case "TPL-COMMISSION_DELIVERER" -> commission(p);
            case "TPL-VOLUME_SLAB" ->
                    throw new TemplateNotMaterializableException(templateCode,
                            "it discounts by the client's monthly volume in XAF, which no pricing input provides yet");
            case "TPL-HUB_STORAGE", "TPL-NETWORK_TRANSIT", "TPL-MARKETPLACE_COMMISSION" ->
                    throw new TemplateNotMaterializableException(templateCode,
                            "it targets hub, network or commission pricing, which is not part of delivery pricing");
            default -> throw new TemplateNotMaterializableException(templateCode, "unknown template");
        };
    }

    // ── Complete templates ──────────────────────────────────────────────────

    private Materialized baseStd(Map<String, String> p) {
        double maxDistance = num(p, "maxDistance", 0);
        PricingRule rule = rule("Tarif standard", maxDistance > 0 ? "distance <= " + trim(maxDistance) : null, 0,
                num(p, "basePrice", 500), num(p, "perKmRate", 50), num(p, "perKgRate", 30),
                num(p, "minPrice", 500));
        return new Materialized(List.of(rule), List.of());
    }

    private Materialized baseZone(Map<String, String> p) {
        double perKm = num(p, "perKmRate", 50);
        String[][] zones = {{"URBAN", "zoneA_basePrice", "500"}, {"PERI_URBAN", "zoneB_basePrice", "800"},
                {"RURAL", "zoneC_basePrice", "1200"}, {"DIPLOMATIC", "zoneD_basePrice", "2000"}};
        List<PricingRule> rules = new ArrayList<>();
        int priority = 0;
        for (String[] z : zones) {
            rules.add(rule("Zone " + z[0], "zoneType == '" + z[0] + "'", priority++,
                    num(p, z[1], Double.parseDouble(z[2])), perKm, 0, 0));
        }
        // Any other zone falls back to the urban price instead of failing the quote.
        rules.add(rule("Zone par défaut", null, priority, num(p, "zoneA_basePrice", 500), perKm, 0, 0));
        return new Materialized(rules, List.of());
    }

    private Materialized fragile(Map<String, String> p) {
        PricingRule rule = rule("Tarif colis fragiles", null, 0, num(p, "basePrice", 800), num(p, "perKmRate", 60), 0, 0);
        List<SurchargeRule> s = new ArrayList<>();
        s.add(surcharge("Supplément fragile", "packageType == 'FRAGILE'", SurchargeType.PERCENTAGE,
                num(p, "fragile_surcharge_pct", 20)));
        s.add(surcharge("Supplément objet précieux", "packageType == 'LUXURY'", SurchargeType.FIXED,
                num(p, "precious_surcharge_fixed", 500)));
        s.add(surcharge("Supplément luxe", "packageType == 'LUXURY'", SurchargeType.PERCENTAGE,
                num(p, "luxury_surcharge_pct", 30)));
        return new Materialized(List.of(rule), s);
    }

    private Materialized perishable(Map<String, String> p) {
        PricingRule rule = rule("Tarif denrées périssables", null, 0, num(p, "basePrice", 1000), num(p, "perKmRate", 80), 0, 0);
        List<SurchargeRule> s = List.of(
                surcharge("Supplément périssable / réfrigéré", "packageType IN [PERISHABLE, REFRIGERATED]",
                        SurchargeType.FIXED, num(p, "perishable_surcharge_fixed", 700)),
                surcharge("Supplément pharmaceutique", "packageType == 'PHARMACEUTICAL'",
                        SurchargeType.FIXED, num(p, "pharmaceutical_surcharge", 500)));
        return new Materialized(List.of(rule), s);
    }

    private Materialized express(Map<String, String> p) {
        PricingRule rule = rule("Tarif express", null, 0, num(p, "basePrice", 700), num(p, "perKmRate", 70), 0, 0);
        List<SurchargeRule> s = List.of(
                surcharge("Express (priorité haute)", "priority == 'HIGH'", SurchargeType.PERCENTAGE, num(p, "express_2h_surcharge_pct", 50)),
                surcharge("Urgent", "priority IN [URGENT, CRITICAL]", SurchargeType.PERCENTAGE, num(p, "express_1h_surcharge_pct", 100)),
                surcharge("Livraison de nuit", "timeOfDay TIME_IS_BETWEEN 22:00 AND 06:00", SurchargeType.FIXED,
                        num(p, "night_surcharge_fixed", 500)),
                surcharge("Livraison le week-end", "isWeekend == true", SurchargeType.FIXED, num(p, "weekend_surcharge_fixed", 300)));
        return new Materialized(List.of(rule), s);
    }

    private Materialized bulk(Map<String, String> p) {
        PricingRule rule = rule("Tarif volume", null, 0, num(p, "basePrice", 1500), num(p, "perKmRate", 100),
                num(p, "perKgRate", 20), 0);
        List<SurchargeRule> s = List.of(
                surcharge("Remise gros poids", "weight >= " + trim(num(p, "bulk_threshold_kg", 20)),
                        SurchargeType.PERCENTAGE, -num(p, "bulk_discount_pct", 15)),
                // A flat rebate: the engine has no per-unit stepping, so the per-parcel rebate is applied once.
                surcharge("Remise multi-colis (forfait)", "packageCount > 5", SurchargeType.FIXED,
                        -num(p, "multi_parcel_discount_per_unit", 50)));
        return new Materialized(List.of(rule), s);
    }

    private Materialized interCity(Map<String, String> p) {
        String cond = "distance >= 50";
        PricingRule rule = rule("Tarif inter-villes", cond, 0, num(p, "basePrice_intercity", 3000),
                num(p, "perKmRate_intercity", 40), 0, 0);
        List<SurchargeRule> s = List.of(
                surcharge("Forfait péages", cond, SurchargeType.FIXED, num(p, "toll_allowance", 500)),
                surcharge("Supplément carburant longue distance", cond, SurchargeType.FIXED,
                        num(p, "fuel_supplement_long_dist", 1000)));
        return new Materialized(List.of(rule), s);
    }

    // ── Adjustment templates ────────────────────────────────────────────────
    // The engine evaluates ONE policy, so an adjustment carries the platform's default delivery grid
    // (500 + 50/km + 30/kg, minimum 500 XAF) as its base; the owner can edit the base by applying a
    // base template afterwards (the most recently activated policy wins).

    private static final double DEFAULT_BASE = 500, DEFAULT_PER_KM = 50, DEFAULT_PER_KG = 30;

    private static PricingRule defaultBase() {
        return rule("Grille de base par défaut", null, 0, DEFAULT_BASE, DEFAULT_PER_KM, DEFAULT_PER_KG, DEFAULT_BASE);
    }

    /** A multiplier of 1.5 is a +50 % surcharge; the engine takes whole percents. */
    private static double pct(double multiplier) {
        return Math.round((multiplier - 1.0) * 100.0);
    }

    private Materialized timeSlots(Map<String, String> p) {
        List<SurchargeRule> s = new ArrayList<>();
        addIfPositive(s, "Majoration nuit (00h-06h)", "timeOfDay TIME_IS_BETWEEN 00:00 AND 06:00",
                pct(num(p, "night_multiplier", 1.5)));
        addIfPositive(s, "Majoration rush matin (06h-09h)", "timeOfDay TIME_IS_BETWEEN 06:00 AND 09:00",
                pct(num(p, "morning_rush_multiplier", 1.2)));
        addIfPositive(s, "Majoration rush soir (17h-20h)", "timeOfDay TIME_IS_BETWEEN 17:00 AND 20:00",
                pct(num(p, "evening_rush_multiplier", 1.3)));
        addIfPositive(s, "Majoration soirée (20h-00h)", "timeOfDay TIME_IS_BETWEEN 20:00 AND 23:59",
                pct(num(p, "late_evening_multiplier", 1.2)));
        addIfPositive(s, "Majoration week-end", "isWeekend == true", pct(num(p, "weekend_multiplier", 1.15)));
        addIfPositive(s, "Majoration jour férié", "isPublicHoliday == true", pct(num(p, "holiday_multiplier", 1.5)));
        return new Materialized(List.of(defaultBase()), s);
    }

    private static void addIfPositive(List<SurchargeRule> into, String name, String condition, double percent) {
        if (percent > 0) into.add(surcharge(name, condition, SurchargeType.PERCENTAGE, percent));
    }

    /**
     * Light/heavy rain and storm map to the engine's weather values. Flooding and extreme heat have no
     * counterpart in the engine's weather model (CLEAR, CLOUDY, RAIN_LIGHT, RAIN_HEAVY, STORM, FOG,
     * HARMATTAN): their parameters are accepted but produce no rule rather than a rule that never matches.
     */
    private Materialized weather(Map<String, String> p) {
        List<SurchargeRule> s = new ArrayList<>();
        addFixedIfPositive(s, "Pluie légère", "weather == 'RAIN_LIGHT'", num(p, "rain_light_surcharge", 200));
        addFixedIfPositive(s, "Pluie forte", "weather == 'RAIN_HEAVY'", num(p, "rain_heavy_surcharge", 500));
        addFixedIfPositive(s, "Tempête / vents forts", "weather == 'STORM'", num(p, "storm_surcharge", 800));
        return new Materialized(List.of(defaultBase()), s);
    }

    private static void addFixedIfPositive(List<SurchargeRule> into, String name, String condition, double amount) {
        if (amount > 0) into.add(surcharge(name, condition, SurchargeType.FIXED, amount));
    }

    /**
     * The engine applies EVERY eligible loyalty rule one after the other, so tiers are encoded as the
     * successive discount that brings the compounded price to each tier's target (3 %, then 4 % more → ≈ 7 %…),
     * rounded to whole percents.
     */
    private Materialized loyalty(Map<String, String> p) {
        String[][] tiers = {{"bronze", "3", "3"}, {"silver", "10", "7"}, {"gold", "20", "12"}, {"platinum", "50", "20"}};
        List<LoyaltyRule> rules = new ArrayList<>();
        double keep = 1.0; // share of the price still paid after the previous tiers
        for (String[] t : tiers) {
            int minTx = (int) num(p, t[0] + "_min_tx", Double.parseDouble(t[1]));
            double target = num(p, t[0] + "_discount_pct", Double.parseDouble(t[2]));
            double wanted = 1.0 - target / 100.0;
            long step = Math.round((1.0 - wanted / keep) * 100.0);
            if (step <= 0) continue; // a tier that does not improve on the previous one adds nothing
            rules.add(LoyaltyRule.builder().id(UUID.randomUUID()).minimumTransactionCount(minTx).periodDays(30)
                    .minimumTotalSpentXAF(BigDecimal.ZERO).discountPercentage(BigDecimal.valueOf(step)).build());
            keep *= 1.0 - step / 100.0;
        }
        return new Materialized(List.of(defaultBase()), List.of(), rules, List.of());
    }

    /** Only the base share is applied by the engine; bonuses, penalties and the guaranteed minimum are not modelled yet. */
    private Materialized commission(Map<String, String> p) {
        CommissionRule rule = CommissionRule.builder().id(UUID.randomUUID())
                .applyToType(CommissionAppliesTo.FREELANCER)
                .platformCommissionPct(BigDecimal.ZERO).agencyCommissionPct(BigDecimal.ZERO)
                .delivererBaseCommissionPct(BigDecimal.valueOf(Math.round(num(p, "base_commission_pct", 60))))
                .bonusRules(List.of()).build();
        return new Materialized(List.of(defaultBase()), List.of(), List.of(), List.of(rule));
    }

    // ── Builders ────────────────────────────────────────────────────────────

    private static PricingRule rule(String name, String condition, int priority, double base, double perKm,
                                    double perKg, double min) {
        return PricingRule.builder()
                .id(UUID.randomUUID()).name(name).conditionExpression(condition).priority(priority)
                .basePrice(money(base))
                .perKmRate(perKm > 0 ? money(perKm) : null)
                .perKgRate(perKg > 0 ? money(perKg) : null)
                .minimumPrice(min > 0 ? money(min) : null)
                .build();
    }

    private static SurchargeRule surcharge(String name, String condition, SurchargeType type, double value) {
        return SurchargeRule.builder()
                .id(UUID.randomUUID()).name(name).conditionExpression(condition)
                .surchargeType(type).value(BigDecimal.valueOf(value)).description(name)
                .build();
    }

    private static Money money(double v) {
        return Money.of(BigDecimal.valueOf(v), CUR);
    }

    private static double num(Map<String, String> p, String key, double fallback) {
        String v = p == null ? null : p.get(key);
        if (v == null || v.isBlank()) return fallback;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parameter '" + key + "' is not a number: " + v);
        }
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
