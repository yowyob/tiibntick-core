package com.yowyob.tiibntick.core.billing.templates.application.service;

import com.yowyob.tiibntick.core.billing.templates.application.command.PreviewPriceCommand;
import com.yowyob.tiibntick.core.billing.templates.domain.model.TemplatePreviewResult;
import com.yowyob.tiibntick.core.billing.templates.domain.model.TemplatePreviewResult.PreviewScenario;
import com.yowyob.tiibntick.core.billing.templates.domain.model.TemplatePreviewResult.SurchargeBreakdownItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Application service that computes a local price preview for billing policy templates.
 *
 * <p>This service implements a lightweight price calculator that applies template
 * parameter values directly to a sample scenario, without requiring the full
 * {@code tnt-billing-pricing} engine. It is used exclusively for the preview
 * feature — the actual BillingPolicy evaluation uses the full engine.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@Service
public class TemplatePriceCalculatorService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * Computes a price preview for the given template code, effective parameters, and scenario.
     *
     * @param templateCode    the catalog template code
     * @param effectiveParams merged parameter map (defaults + custom overrides)
     * @param command         the preview command containing the sample scenario
     * @return the computed preview result with full breakdown
     */
    public TemplatePreviewResult compute(
            String templateCode,
            Map<String, String> effectiveParams,
            PreviewPriceCommand command) {

        log.debug("Computing preview for template {} with params {} and scenario distanceKm={}, weightKg={}",
                templateCode, effectiveParams.keySet(), command.getDistanceKm(), command.getWeightKg());

        List<SurchargeBreakdownItem> surcharges = new ArrayList<>();

        BigDecimal base = computeBase(templateCode, effectiveParams, command, surcharges);
        BigDecimal distanceCost = computeDistance(templateCode, effectiveParams, command);
        BigDecimal weightCost = computeWeight(templateCode, effectiveParams, command);

        BigDecimal subtotal = base.add(distanceCost).add(weightCost);
        subtotal = applyTemplateSurcharges(templateCode, effectiveParams, command, subtotal, surcharges);

        BigDecimal totalSurcharges = surcharges.stream()
                .map(SurchargeBreakdownItem::getAmountXaf)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal totalPrice = base.add(distanceCost).add(weightCost).add(totalSurcharges);

        // Apply minimum price
        BigDecimal minPrice = getDecimal(effectiveParams, "minPrice", "0");
        boolean aboveMin = totalPrice.compareTo(minPrice) >= 0;
        if (!aboveMin) {
            totalPrice = minPrice;
        }

        PreviewScenario scenario = PreviewScenario.builder()
                .distanceKm(command.getDistanceKm())
                .weightKg(command.getWeightKg())
                .packageType(command.getPackageType())
                .priority(command.getPriority())
                .clientTransactionCount(command.getClientTransactionCount())
                .deliveryZoneType(command.getDeliveryZoneType())
                .weatherCondition(command.getWeatherCondition())
                .paymentMethod(command.getPaymentMethod())
                .requiresRefrigeration(command.isRequiresRefrigeration())
                .requiresAssembly(command.isRequiresAssembly())
                .requiresIDCheck(command.isRequiresIDCheck())
                .timeOfDay(command.getTimeOfDay())
                .dayOfWeek(command.getDayOfWeek())
                .build();

        return TemplatePreviewResult.builder()
                .templateCode(templateCode)
                .totalPriceXaf(totalPrice.setScale(0, RoundingMode.HALF_UP))
                .basePriceXaf(base)
                .distanceCostXaf(distanceCost)
                .weightCostXaf(weightCost)
                .appliedSurcharges(surcharges)
                .totalSurchargesXaf(totalSurcharges)
                .aboveMinimumPrice(aboveMin)
                .minimumPriceXaf(minPrice)
                .currency("XAF")
                .scenario(scenario)
                .build();
    }

    // ─── Component calculators ─────────────────────────────────────────────

    private BigDecimal computeBase(
            String code, Map<String, String> p, PreviewPriceCommand cmd,
            List<SurchargeBreakdownItem> surcharges) {

        // Zone-specific base price
        if ("TPL-BASE-ZONE".equals(code)) {
            return switch (cmd.getDeliveryZoneType()) {
                case "PERI_URBAN"  -> getDecimal(p, "zoneB_basePrice", "800");
                case "RURAL"       -> getDecimal(p, "zoneC_basePrice", "1200");
                case "DIPLOMATIC"  -> getDecimal(p, "zoneD_basePrice", "2000");
                default            -> getDecimal(p, "zoneA_basePrice", "500");  // URBAN
            };
        }

        if ("TPL-INTER_CITY".equals(code)) {
            return getDecimal(p, "basePrice_intercity", "3000");
        }

        return getDecimal(p, "basePrice", "500");
    }

    private BigDecimal computeDistance(String code, Map<String, String> p, PreviewPriceCommand cmd) {
        if ("TPL-HUB_STORAGE".equals(code) || "TPL-NETWORK_TRANSIT".equals(code)
                || "TPL-LOYALTY_PROGRESSIVE".equals(code) || "TPL-VOLUME_SLAB".equals(code)
                || "TPL-COMMISSION_DELIVERER".equals(code) || "TPL-MARKETPLACE_COMMISSION".equals(code)) {
            return ZERO;
        }

        String rateKey = "TPL-INTER_CITY".equals(code) ? "perKmRate_intercity" : "perKmRate";
        BigDecimal rate = getDecimal(p, rateKey, "50");
        return rate.multiply(BigDecimal.valueOf(cmd.getDistanceKm())).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeWeight(String code, Map<String, String> p, PreviewPriceCommand cmd) {
        if (!p.containsKey("perKgRate")) return ZERO;
        BigDecimal rate = getDecimal(p, "perKgRate", "0");
        return rate.multiply(BigDecimal.valueOf(cmd.getWeightKg())).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal applyTemplateSurcharges(
            String code, Map<String, String> p, PreviewPriceCommand cmd,
            BigDecimal subtotal, List<SurchargeBreakdownItem> surcharges) {

        switch (code) {
            case "TPL-FRAGILE" -> {
                if ("FRAGILE".equals(cmd.getPackageType())) {
                    BigDecimal pct = getDecimal(p, "fragile_surcharge_pct", "20");
                    BigDecimal amount = subtotal.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                    surcharges.add(SurchargeBreakdownItem.builder()
                            .code("FRAGILE").labelFr("Colis fragile").labelEn("Fragile package")
                            .amountXaf(amount).unit("%").build());
                }
                if ("LUXURY".equals(cmd.getPackageType())) {
                    BigDecimal fixed = getDecimal(p, "precious_surcharge_fixed", "500");
                    BigDecimal pct = getDecimal(p, "luxury_surcharge_pct", "30");
                    BigDecimal pctAmt = subtotal.add(fixed).multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                    surcharges.add(SurchargeBreakdownItem.builder()
                            .code("LUXURY").labelFr("Colis luxe").labelEn("Luxury item")
                            .amountXaf(fixed.add(pctAmt)).unit("XAF+%").build());
                }
            }
            case "TPL-PERISHABLE" -> {
                if ("PERISHABLE".equals(cmd.getPackageType()) || "REFRIGERATED".equals(cmd.getPackageType())) {
                    BigDecimal s = getDecimal(p, "perishable_surcharge_fixed", "700");
                    surcharges.add(SurchargeBreakdownItem.builder()
                            .code("PERISHABLE").labelFr("Périssable réfrigéré").labelEn("Perishable/refrigerated")
                            .amountXaf(s).unit("XAF").build());
                }
                if ("PHARMACEUTICAL".equals(cmd.getPackageType())) {
                    BigDecimal s = getDecimal(p, "pharmaceutical_surcharge", "500");
                    surcharges.add(SurchargeBreakdownItem.builder()
                            .code("PHARMACEUTICAL").labelFr("Pharmaceutique").labelEn("Pharmaceutical")
                            .amountXaf(s).unit("XAF").build());
                }
            }
            case "TPL-EXPRESS" -> {
                if ("EXPRESS".equals(cmd.getPriority())) {
                    BigDecimal pct = getDecimal(p, "express_2h_surcharge_pct", "50");
                    BigDecimal amount = subtotal.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                    surcharges.add(SurchargeBreakdownItem.builder()
                            .code("EXPRESS_2H").labelFr("Livraison express 2h").labelEn("2h express delivery")
                            .amountXaf(amount).unit("%").build());
                }
                if (isNightTime(cmd.getTimeOfDay())) {
                    BigDecimal s = getDecimal(p, "night_surcharge_fixed", "500");
                    surcharges.add(SurchargeBreakdownItem.builder()
                            .code("NIGHT").labelFr("Surcharge nuit").labelEn("Night surcharge")
                            .amountXaf(s).unit("XAF").build());
                }
            }
            case "TPL-WEATHER_ADAPTIVE" -> {
                String weather = cmd.getWeatherCondition();
                String key = switch (weather) {
                    case "RAIN_LIGHT" -> "rain_light_surcharge";
                    case "RAIN_HEAVY" -> "rain_heavy_surcharge";
                    case "FLOOD"      -> "flood_surcharge";
                    case "STORM"      -> "storm_surcharge";
                    default -> null;
                };
                if (key != null) {
                    BigDecimal s = getDecimal(p, key, "0");
                    surcharges.add(SurchargeBreakdownItem.builder()
                            .code("WEATHER_" + weather).labelFr("Surcharge météo").labelEn("Weather surcharge")
                            .amountXaf(s).unit("XAF").build());
                }
            }
            case "TPL-LOYALTY_PROGRESSIVE" -> {
                int txCount = cmd.getClientTransactionCount();
                int platMin = getInt(p, "platinum_min_tx", 50);
                int goldMin  = getInt(p, "gold_min_tx", 20);
                int silvMin  = getInt(p, "silver_min_tx", 10);
                int bronMin  = getInt(p, "bronze_min_tx", 3);

                if (txCount >= platMin) {
                    BigDecimal pct = getDecimal(p, "platinum_discount_pct", "20");
                    BigDecimal disc = subtotal.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP).negate();
                    surcharges.add(SurchargeBreakdownItem.builder()
                            .code("LOYALTY_PLATINUM").labelFr("Remise fidélité Platine").labelEn("Platinum loyalty discount")
                            .amountXaf(disc).unit("%").build());
                } else if (txCount >= goldMin) {
                    BigDecimal pct = getDecimal(p, "gold_discount_pct", "12");
                    BigDecimal disc = subtotal.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP).negate();
                    surcharges.add(SurchargeBreakdownItem.builder()
                            .code("LOYALTY_GOLD").labelFr("Remise fidélité Or").labelEn("Gold loyalty discount")
                            .amountXaf(disc).unit("%").build());
                } else if (txCount >= silvMin) {
                    BigDecimal pct = getDecimal(p, "silver_discount_pct", "7");
                    BigDecimal disc = subtotal.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP).negate();
                    surcharges.add(SurchargeBreakdownItem.builder()
                            .code("LOYALTY_SILVER").labelFr("Remise fidélité Argent").labelEn("Silver loyalty discount")
                            .amountXaf(disc).unit("%").build());
                } else if (txCount >= bronMin) {
                    BigDecimal pct = getDecimal(p, "bronze_discount_pct", "3");
                    BigDecimal disc = subtotal.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP).negate();
                    surcharges.add(SurchargeBreakdownItem.builder()
                            .code("LOYALTY_BRONZE").labelFr("Remise fidélité Bronze").labelEn("Bronze loyalty discount")
                            .amountXaf(disc).unit("%").build());
                }
            }
            case "TPL-TIME_SLOTS" -> {
                BigDecimal mult = resolveTimeMultiplier(p, cmd);
                if (mult.compareTo(BigDecimal.ONE) != 0) {
                    BigDecimal delta = subtotal.multiply(mult.subtract(BigDecimal.ONE)).setScale(2, RoundingMode.HALF_UP);
                    surcharges.add(SurchargeBreakdownItem.builder()
                            .code("TIME_MULTIPLIER").labelFr("Multiplicateur horaire").labelEn("Time slot multiplier")
                            .amountXaf(delta).unit("x").build());
                }
            }
        }
        return subtotal;
    }

    // ─── Utility helpers ───────────────────────────────────────────────────

    private BigDecimal getDecimal(Map<String, String> p, String key, String fallback) {
        String val = p.getOrDefault(key, fallback);
        try { return new BigDecimal(val); } catch (NumberFormatException e) { return new BigDecimal(fallback); }
    }

    private int getInt(Map<String, String> p, String key, int fallback) {
        try { return Integer.parseInt(p.getOrDefault(key, String.valueOf(fallback))); }
        catch (NumberFormatException e) { return fallback; }
    }

    private boolean isNightTime(String timeOfDay) {
        if (timeOfDay == null) return false;
        String[] parts = timeOfDay.split(":");
        if (parts.length < 2) return false;
        int hour = Integer.parseInt(parts[0]);
        return hour >= 22 || hour < 6;
    }

    private BigDecimal resolveTimeMultiplier(Map<String, String> p, PreviewPriceCommand cmd) {
        if (cmd.isPublicHoliday()) return getDecimal(p, "holiday_multiplier", "1.5");
        boolean isWeekend = "SATURDAY".equals(cmd.getDayOfWeek()) || "SUNDAY".equals(cmd.getDayOfWeek());
        if (isWeekend) return getDecimal(p, "weekend_multiplier", "1.15");
        if (isNightTime(cmd.getTimeOfDay())) return getDecimal(p, "night_multiplier", "1.5");
        return BigDecimal.ONE;
    }
}
