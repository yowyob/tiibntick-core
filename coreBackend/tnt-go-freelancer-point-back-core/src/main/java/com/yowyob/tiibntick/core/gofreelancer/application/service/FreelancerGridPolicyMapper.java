package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PricingRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.SurchargeRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.SurchargeType;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerPricingDTO;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerPricingPolicy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Translates the freelancer's flat rate grid (base fee, price per km/kg/m³, waiting fee,
 * fragile/perishable surcharges) to and from a {@link BillingPolicy}, so that the grid the
 * freelancer edits is the very policy the billing engine prices with.
 *
 * <p>A grid policy is recognisable by {@link #GRID_TEMPLATE_CODE}; it holds one unconditional
 * {@link PricingRule} and up to two {@link SurchargeRule}s whose DSL conditions are
 * {@link #FRAGILE_CONDITION} and {@link #PERISHABLE_CONDITION}.
 *
 * @author MANFOUO BRAUN
 */
public final class FreelancerGridPolicyMapper {

    public static final String GRID_TEMPLATE_CODE = "FREELANCER-GRID";
    public static final String FRAGILE_CONDITION = "packageType == 'FRAGILE'";
    public static final String PERISHABLE_CONDITION = "packageType == 'PERISHABLE'";
    public static final String DEFAULT_CURRENCY = "XAF";

    /** Platform barème used when a freelancer has published nothing (priced by the same engine). */
    public static final FreelancerPricingDTO PLATFORM_DEFAULT_GRID = FreelancerPricingDTO.builder()
            .baseFee(500.0).pricePerKm(100.0).pricePerKg(50.0).pricePerCbm(2000.0)
            .fragileSurcharge(500.0).perishableSurcharge(500.0).waitingFeePerMinute(0.0)
            .currency(DEFAULT_CURRENCY).build();

    private FreelancerGridPolicyMapper() { }

    /** FCFA, F CFA, blank or unknown codes are the CFA franc BEAC, ISO code XAF. */
    public static String normalizeCurrency(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_CURRENCY;
        String c = raw.trim().toUpperCase().replace(" ", "");
        if (c.equals("FCFA") || c.equals("CFA") || c.equals("FRCFA")) return DEFAULT_CURRENCY;
        try {
            java.util.Currency.getInstance(c);
            return c;
        } catch (IllegalArgumentException e) {
            return DEFAULT_CURRENCY;
        }
    }

    /** Legacy {@code delivery_person_pricing} row → grid DTO. */
    public static FreelancerPricingDTO toGrid(FreelancerPricingPolicy legacy) {
        return FreelancerPricingDTO.builder()
                .pricePerKg(legacy.getPricePerKg()).pricePerCbm(legacy.getPricePerCbm())
                .pricePerKm(legacy.getPricePerKm()).fragileSurcharge(legacy.getFragileSurcharge())
                .perishableSurcharge(legacy.getPerishableSurcharge()).baseFee(legacy.getBaseFee())
                .waitingFeePerMinute(0.0)
                .currency(normalizeCurrency(legacy.getCurrency())).source("LEGACY").build();
    }

    /**
     * Billing policy → grid DTO (best effort for policies not authored as a grid: the first rule by
     * priority is projected; conditions and extra rules are not representable in a flat grid).
     */
    public static FreelancerPricingDTO toGrid(BillingPolicy policy) {
        PricingRule rule = policy.sortedPricingRules().stream().findFirst().orElse(null);
        String currency = policy.currencyCode();
        return FreelancerPricingDTO.builder()
                .baseFee(amount(rule == null ? null : rule.getBasePrice()))
                .pricePerKm(amount(rule == null ? null : rule.getPerKmRate()))
                .pricePerKg(amount(rule == null ? null : rule.getPerKgRate()))
                .pricePerCbm(amount(rule == null ? null : rule.getPerCbmRate()))
                .waitingFeePerMinute(amount(rule == null ? null : rule.getWaitingFeePerMinute()))
                .fragileSurcharge(surcharge(policy, "FRAGILE"))
                .perishableSurcharge(surcharge(policy, "PERISHABLE"))
                .currency(currency)
                .policyId(policy.getId())
                .source(GRID_TEMPLATE_CODE.equals(policy.getTemplateCode()) ? "POLICY"
                        : "TEMPLATE:" + policy.getTemplateCode())
                .build();
    }

    /** Fills the gaps of {@code base} with {@code overrides}; absent numbers default to 0. */
    public static FreelancerPricingDTO merge(FreelancerPricingDTO base, FreelancerPricingDTO overrides) {
        return FreelancerPricingDTO.builder()
                .baseFee(pick(overrides.getBaseFee(), base.getBaseFee()))
                .pricePerKm(pick(overrides.getPricePerKm(), base.getPricePerKm()))
                .pricePerKg(pick(overrides.getPricePerKg(), base.getPricePerKg()))
                .pricePerCbm(pick(overrides.getPricePerCbm(), base.getPricePerCbm()))
                .waitingFeePerMinute(pick(overrides.getWaitingFeePerMinute(), base.getWaitingFeePerMinute()))
                .fragileSurcharge(pick(overrides.getFragileSurcharge(), base.getFragileSurcharge()))
                .perishableSurcharge(pick(overrides.getPerishableSurcharge(), base.getPerishableSurcharge()))
                .currency(normalizeCurrency(overrides.getCurrency() != null ? overrides.getCurrency()
                        : base.getCurrency()))
                .policyId(base.getPolicyId()).source(base.getSource())
                .build();
    }

    /** The pricing and surcharge rules that express {@code grid}. */
    public static Rules toRules(FreelancerPricingDTO grid) {
        String cur = normalizeCurrency(grid.getCurrency());
        PricingRule rule = PricingRule.builder()
                .id(UUID.randomUUID()).name("Grille tarifaire du livreur").priority(1)
                .basePrice(money(grid.getBaseFee(), cur, true))
                .perKmRate(money(grid.getPricePerKm(), cur, false))
                .perKgRate(money(grid.getPricePerKg(), cur, false))
                .perCbmRate(money(grid.getPricePerCbm(), cur, false))
                .waitingFeePerMinute(money(grid.getWaitingFeePerMinute(), cur, false))
                .build();
        List<SurchargeRule> surcharges = new ArrayList<>();
        addSurcharge(surcharges, "Supplément fragile", FRAGILE_CONDITION, grid.getFragileSurcharge());
        addSurcharge(surcharges, "Supplément périssable", PERISHABLE_CONDITION, grid.getPerishableSurcharge());
        return new Rules(List.of(rule), surcharges);
    }

    /** A brand-new grid policy (DRAFT; the caller activates it). */
    public static BillingPolicy newPolicy(String ownerId, UUID tenantId, FreelancerPricingDTO grid) {
        Rules rules = toRules(grid);
        return BillingPolicy.builder()
                .id(UUID.randomUUID()).tenantId(tenantId)
                .name("Grille livreur " + ownerId).description("Grille tarifaire du livreur (base, km, kg, m³, attente, suppléments)")
                .pricingRules(rules.pricingRules()).surchargeRules(rules.surchargeRules())
                .promotions(List.of()).loyaltyRules(List.of()).commissionRules(List.of())
                .specialSurcharges(List.of()).hubStorageRules(List.of()).networkTransitRules(List.of())
                .status(PolicyStatus.DRAFT).validFrom(LocalDate.now())
                .ownerType(PolicyOwnerType.FREELANCER_ORG).ownerActorId(ownerId)
                .isFromTemplate(false).templateCode(GRID_TEMPLATE_CODE)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    /** An unsaved, active policy used to price a grid that has not (yet) been migrated. */
    public static BillingPolicy ephemeralPolicy(String ownerId, FreelancerPricingDTO grid) {
        return newPolicy(ownerId, null, grid).withStatus(PolicyStatus.ACTIVE);
    }

    /** Most recently touched policy wins when a freelancer owns several active ones. */
    public static final Comparator<BillingPolicy> BY_FRESHNESS = Comparator.comparing(
            (BillingPolicy p) -> p.getUpdatedAt() != null ? p.getUpdatedAt()
                    : p.getCreatedAt() != null ? p.getCreatedAt() : Instant.EPOCH);

    public record Rules(List<PricingRule> pricingRules, List<SurchargeRule> surchargeRules) { }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void addSurcharge(List<SurchargeRule> out, String name, String condition, Double value) {
        if (value == null || value <= 0) return;
        out.add(SurchargeRule.builder().id(UUID.randomUUID()).name(name).conditionExpression(condition)
                .surchargeType(SurchargeType.FIXED).value(BigDecimal.valueOf(value)).description(name).build());
    }

    private static Double surcharge(BillingPolicy policy, String marker) {
        if (policy.getSurchargeRules() == null) return 0.0;
        return policy.getSurchargeRules().stream()
                .filter(s -> s.getConditionExpression() != null && s.getConditionExpression().contains(marker))
                .filter(s -> SurchargeType.FIXED.equals(s.getSurchargeType()) && s.getValue() != null)
                .map(s -> s.getValue().doubleValue()).findFirst().orElse(0.0);
    }

    private static Money money(Double v, String currency, boolean required) {
        double d = v == null ? 0.0 : v;
        if (d <= 0 && !required) return null;
        return Money.of(BigDecimal.valueOf(d), currency);
    }

    private static Double amount(Money m) {
        return m == null ? 0.0 : m.getAmount().doubleValue();
    }

    private static Double pick(Double override, Double base) {
        return override != null ? override : base != null ? base : 0.0;
    }
}
