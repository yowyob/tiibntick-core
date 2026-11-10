package com.yowyob.tiibntick.core.billing.templates.application.service;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PricingRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.SurchargeRule;
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
            "TPL-EXPRESS", "TPL-BULK", "TPL-INTER_CITY");

    private static final String CUR = "XAF";

    /** Rules ready to be put in a {@code BillingPolicy}. */
    public record Materialized(List<PricingRule> pricingRules, List<SurchargeRule> surchargeRules) { }

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
            case "TPL-LOYALTY_PROGRESSIVE", "TPL-TIME_SLOTS", "TPL-WEATHER_ADAPTIVE", "TPL-VOLUME_SLAB" ->
                    throw new TemplateNotMaterializableException(templateCode,
                            "it only adjusts a price and has no base price of its own; the pricing engine "
                                    + "evaluates a single policy, so it cannot be combined with another template yet");
            case "TPL-HUB_STORAGE", "TPL-NETWORK_TRANSIT", "TPL-MARKETPLACE_COMMISSION", "TPL-COMMISSION_DELIVERER" ->
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
