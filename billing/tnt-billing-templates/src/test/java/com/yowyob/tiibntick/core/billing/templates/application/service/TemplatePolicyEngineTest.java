package com.yowyob.tiibntick.core.billing.templates.application.service;

import com.yowyob.tiibntick.core.billing.dsl.application.service.DslCompilerService;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DeliveryPriority;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PackageType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.evaluator.DslEvaluator;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.DslLexer;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.parser.DslParser;
import com.yowyob.tiibntick.core.billing.pricing.application.service.PricingEngineService;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PriceEvaluation;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateNotMaterializableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rules a template materializes are evaluated by the REAL pricing engine and the REAL DSL
 * parser — no mocks. This is the guard against the pseudo-DSL drift: a condition written with a
 * word the parser does not know would silently never match (the engine swallows evaluation errors).
 */
class TemplatePolicyEngineTest {

    private final TemplatePolicyMaterializer materializer = new TemplatePolicyMaterializer();
    private final DslCompilerService compiler = new DslCompilerService(new DslLexer(), new DslParser());
    private PricingEngineService engine;

    @BeforeEach
    void setUp() {
        engine = new PricingEngineService(null, compiler, new DslEvaluator());
    }

    private BillingPolicy policy(String code, Map<String, String> params) {
        var m = materializer.materialize(code, params);
        return BillingPolicy.builder().id(UUID.randomUUID()).tenantId(UUID.randomUUID()).name(code)
                .pricingRules(m.pricingRules()).surchargeRules(m.surchargeRules())
                .promotions(List.of()).loyaltyRules(m.loyaltyRules()).commissionRules(m.commissionRules())
                .specialSurcharges(List.of()).hubStorageRules(List.of()).networkTransitRules(List.of())
                .status(PolicyStatus.ACTIVE).validFrom(LocalDate.now()).build();
    }

    private PricingContext.PricingContextBuilder ctx() {
        return PricingContext.builder().distanceKm(10).weightKg(2).packageTypes(List.of(PackageType.STANDARD))
                .priority(DeliveryPriority.NORMAL).timeOfDay(LocalTime.NOON).dayOfWeek(DayOfWeek.TUESDAY)
                .deliveryZoneType("URBAN").packageCount(1).clientTxCount(0);
    }

    private long price(String code, Map<String, String> params, PricingContext c) {
        PriceEvaluation ev = engine.evaluateWith(policy(code, params), c).block();
        return ev.getSellingPrice().getAmount().longValue();
    }

    @Test
    @DisplayName("every supported template's conditions parse with the real DSL grammar")
    void everyConditionParses() {
        for (String code : TemplatePolicyMaterializer.SUPPORTED) {
            var m = materializer.materialize(code, Map.of());
            m.pricingRules().forEach(r -> {
                if (r.getConditionExpression() != null) compiler.compileCondition(r.getConditionExpression());
            });
            m.surchargeRules().forEach(r -> compiler.compileCondition(r.getConditionExpression()));
        }
    }

    @Test
    @DisplayName("TPL-BASE-STD: base 500 + 10 km × 50 + 2 kg × 30 = 1060")
    void baseStd() {
        assertThat(price("TPL-BASE-STD", Map.of(), ctx().build())).isEqualTo(1060);
    }

    @Test
    @DisplayName("TPL-BASE-STD honours the customised parameters")
    void baseStdCustom() {
        assertThat(price("TPL-BASE-STD", Map.of("basePrice", "700", "perKmRate", "60", "perKgRate", "0"), ctx().build()))
                .isEqualTo(1300);
    }

    @Test
    @DisplayName("TPL-BASE-STD refuses a delivery beyond maxDistance")
    void baseStdMaxDistance() {
        assertThatThrownBy(() -> price("TPL-BASE-STD", Map.of("maxDistance", "5"), ctx().build()))
                .hasMessageContaining("No PricingRule matched");
    }

    @Test
    @DisplayName("TPL-BASE-ZONE: a rural delivery uses the rural base price, an unknown zone the urban one")
    void baseZone() {
        assertThat(price("TPL-BASE-ZONE", Map.of(), ctx().deliveryZoneType("RURAL").build())).isEqualTo(1200 + 500);
        assertThat(price("TPL-BASE-ZONE", Map.of(), ctx().deliveryZoneType("MARS").build())).isEqualTo(500 + 500);
    }

    @Test
    @DisplayName("TPL-FRAGILE: a fragile parcel gets +20 % on the subtotal, a standard one does not")
    void fragile() {
        long standard = price("TPL-FRAGILE", Map.of(), ctx().build());
        long fragile = price("TPL-FRAGILE", Map.of(), ctx().packageTypes(List.of(PackageType.FRAGILE)).build());
        assertThat(standard).isEqualTo(800 + 600);
        assertThat(fragile).isEqualTo(Math.round(standard * 1.2));
    }

    @Test
    @DisplayName("TPL-FRAGILE: a LUXURY parcel gets the fixed and the percentage surcharge")
    void luxury() {
        assertThat(price("TPL-FRAGILE", Map.of(), ctx().packageTypes(List.of(PackageType.LUXURY)).build()))
                .isGreaterThan(price("TPL-FRAGILE", Map.of(), ctx().build()) + 500);
    }

    @Test
    @DisplayName("TPL-PERISHABLE: refrigerated and pharmaceutical parcels are surcharged")
    void perishable() {
        long base = price("TPL-PERISHABLE", Map.of(), ctx().build());
        assertThat(base).isEqualTo(1000 + 800);
        assertThat(price("TPL-PERISHABLE", Map.of(), ctx().packageTypes(List.of(PackageType.REFRIGERATED)).build()))
                .isEqualTo(base + 700);
        assertThat(price("TPL-PERISHABLE", Map.of(), ctx().packageTypes(List.of(PackageType.PHARMACEUTICAL)).build()))
                .isEqualTo(base + 500);
    }

    @Test
    @DisplayName("TPL-EXPRESS: night, weekend and urgent surcharges apply")
    void express() {
        long base = price("TPL-EXPRESS", Map.of(), ctx().build());
        assertThat(base).isEqualTo(700 + 700);
        assertThat(price("TPL-EXPRESS", Map.of(), ctx().timeOfDay(LocalTime.of(23, 30)).build())).isEqualTo(base + 500);
        assertThat(price("TPL-EXPRESS", Map.of(), ctx().timeOfDay(LocalTime.of(3, 0)).build())).isEqualTo(base + 500);
        assertThat(price("TPL-EXPRESS", Map.of(), ctx().dayOfWeek(DayOfWeek.SATURDAY).build())).isEqualTo(base + 300);
        assertThat(price("TPL-EXPRESS", Map.of(), ctx().priority(DeliveryPriority.URGENT).build())).isEqualTo(base * 2);
    }

    @Test
    @DisplayName("TPL-BULK: a heavy parcel gets the bulk discount")
    void bulk() {
        long light = price("TPL-BULK", Map.of(), ctx().weightKg(10).build());
        long heavy = price("TPL-BULK", Map.of(), ctx().weightKg(30).build());
        assertThat(light).isEqualTo(1500 + 1000 + 200);
        assertThat(heavy).isEqualTo(Math.round((1500 + 1000 + 600) * 0.85));
    }

    @Test
    @DisplayName("TPL-INTER_CITY applies from 50 km with tolls and fuel supplement, and not below")
    void interCity() {
        assertThat(price("TPL-INTER_CITY", Map.of(), ctx().distanceKm(100).build())).isEqualTo(3000 + 4000 + 500 + 1000);
        assertThatThrownBy(() -> price("TPL-INTER_CITY", Map.of(), ctx().distanceKm(10).build()))
                .hasMessageContaining("No PricingRule matched");
    }

    private PriceEvaluation evaluate(String code, Map<String, String> params, PricingContext c) {
        return engine.evaluateWith(policy(code, params), c).block();
    }

    @Test
    @DisplayName("TPL-TIME_SLOTS: night, rush, weekend and holiday multipliers apply on top of the default grid")
    void timeSlots() {
        long base = price("TPL-TIME_SLOTS", Map.of(), ctx().build());   // 500 + 10×50 + 2×30
        assertThat(base).isEqualTo(1060);
        assertThat(price("TPL-TIME_SLOTS", Map.of(), ctx().timeOfDay(LocalTime.of(3, 0)).build()))
                .isEqualTo(Math.round(base * 1.5));
        assertThat(price("TPL-TIME_SLOTS", Map.of(), ctx().timeOfDay(LocalTime.of(7, 30)).build()))
                .isEqualTo(Math.round(base * 1.2));
        assertThat(price("TPL-TIME_SLOTS", Map.of(), ctx().timeOfDay(LocalTime.of(18, 0)).build()))
                .isEqualTo(Math.round(base * 1.3));
        assertThat(price("TPL-TIME_SLOTS", Map.of(), ctx().timeOfDay(LocalTime.of(21, 0)).build()))
                .isEqualTo(Math.round(base * 1.2));
        assertThat(price("TPL-TIME_SLOTS", Map.of(), ctx().dayOfWeek(DayOfWeek.SATURDAY).build()))
                .isEqualTo(Math.round(base * 1.15));
        assertThat(price("TPL-TIME_SLOTS", Map.of(), ctx().isPublicHoliday(true).build()))
                .isEqualTo(Math.round(base * 1.5));
        assertThat(price("TPL-TIME_SLOTS", Map.of("night_multiplier", "2.0"), ctx().timeOfDay(LocalTime.of(3, 0)).build()))
                .isEqualTo(base * 2);
    }

    @Test
    @DisplayName("TPL-WEATHER_ADAPTIVE: rain and storm add their fixed surcharge, clear weather none")
    void weather() {
        long base = price("TPL-WEATHER_ADAPTIVE", Map.of(), ctx().build());
        assertThat(base).isEqualTo(1060);
        assertThat(price("TPL-WEATHER_ADAPTIVE", Map.of(), ctx().weatherCondition(
                com.yowyob.tiibntick.core.billing.dsl.domain.model.WeatherCondition.RAIN_LIGHT).build())).isEqualTo(base + 200);
        assertThat(price("TPL-WEATHER_ADAPTIVE", Map.of(), ctx().weatherCondition(
                com.yowyob.tiibntick.core.billing.dsl.domain.model.WeatherCondition.RAIN_HEAVY).build())).isEqualTo(base + 500);
        assertThat(price("TPL-WEATHER_ADAPTIVE", Map.of(), ctx().weatherCondition(
                com.yowyob.tiibntick.core.billing.dsl.domain.model.WeatherCondition.STORM).build())).isEqualTo(base + 800);
        assertThat(price("TPL-WEATHER_ADAPTIVE", Map.of(), ctx().weatherCondition(
                com.yowyob.tiibntick.core.billing.dsl.domain.model.WeatherCondition.CLEAR).build())).isEqualTo(base);
    }

    @Test
    @DisplayName("TPL-LOYALTY_PROGRESSIVE: the discount grows with the client's transactions and lands on each tier's target")
    void loyalty() {
        long base = price("TPL-LOYALTY_PROGRESSIVE", Map.of(), ctx().clientTxCount(0).build());
        assertThat(base).isEqualTo(1060);
        long bronze = price("TPL-LOYALTY_PROGRESSIVE", Map.of(), ctx().clientTxCount(3).build());
        long silver = price("TPL-LOYALTY_PROGRESSIVE", Map.of(), ctx().clientTxCount(10).build());
        long gold = price("TPL-LOYALTY_PROGRESSIVE", Map.of(), ctx().clientTxCount(20).build());
        long platinum = price("TPL-LOYALTY_PROGRESSIVE", Map.of(), ctx().clientTxCount(50).build());
        assertThat(base).isGreaterThan(bronze);
        assertThat(bronze).isGreaterThan(silver);
        assertThat(silver).isGreaterThan(gold);
        assertThat(gold).isGreaterThan(platinum);
        // compounded whole-percent steps stay within one point of the 3 / 7 / 12 / 20 % targets
        assertThat((double) bronze / base).isBetween(0.96, 0.98);
        assertThat((double) silver / base).isBetween(0.92, 0.94);
        assertThat((double) gold / base).isBetween(0.87, 0.89);
        assertThat((double) platinum / base).isBetween(0.79, 0.81);
    }

    @Test
    @DisplayName("TPL-COMMISSION_DELIVERER: the engine computes the deliverer's share of the selling price")
    void commissionDeliverer() {
        PriceEvaluation ev = evaluate("TPL-COMMISSION_DELIVERER", Map.of("base_commission_pct", "70"), ctx().build());
        assertThat(ev.getSellingPrice().getAmount().longValue()).isEqualTo(1060);
        assertThat(ev.getDelivererCommission().getAmount().longValue()).isEqualTo(742);
    }

    @Test
    @DisplayName("every adjustment template parses with the real grammar and carries a priceable base")
    void adjustmentsParse() {
        for (String code : TemplatePolicyMaterializer.ADJUSTMENT_ONLY) {
            var m = materializer.materialize(code, Map.of());
            assertThat(m.pricingRules()).isNotEmpty();
            m.surchargeRules().forEach(r -> compiler.compileCondition(r.getConditionExpression()));
        }
    }

    @Test
    @DisplayName("templates the engine cannot price on a delivery are refused instead of priced at zero")
    void unsupportedAreRefused() {
        for (String code : List.of("TPL-VOLUME_SLAB", "TPL-HUB_STORAGE", "TPL-NETWORK_TRANSIT",
                "TPL-MARKETPLACE_COMMISSION", "TPL-NOPE")) {
            assertThatThrownBy(() -> materializer.materialize(code, Map.of()))
                    .isInstanceOf(TemplateNotMaterializableException.class);
        }
    }
}
