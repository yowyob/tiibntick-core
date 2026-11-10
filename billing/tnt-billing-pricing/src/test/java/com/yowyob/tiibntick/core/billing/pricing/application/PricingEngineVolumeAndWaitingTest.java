package com.yowyob.tiibntick.core.billing.pricing.application;

import com.yowyob.tiibntick.core.billing.dsl.application.service.DslCompilerService;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PackageType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.evaluator.DslEvaluator;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.DslLexer;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.parser.DslParser;
import com.yowyob.tiibntick.core.billing.pricing.application.service.PricingEngineService;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PriceEvaluation;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PricingRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.SurchargeRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.LineItemType;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.SurchargeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The freelancer grid components the engine did not know: price per m³ and waiting time. */
class PricingEngineVolumeAndWaitingTest {

    private final PricingEngineService engine = new PricingEngineService(null,
            new DslCompilerService(new DslLexer(), new DslParser()), new DslEvaluator());

    private static Money xaf(long v) {
        return Money.of(BigDecimal.valueOf(v), "XAF");
    }

    private BillingPolicy draftPolicy() {
        PricingRule rule = PricingRule.builder().id(UUID.randomUUID()).name("grid").priority(0)
                .basePrice(xaf(500)).perKmRate(xaf(100)).perKgRate(xaf(50))
                .perCbmRate(xaf(2000)).waitingFeePerMinute(xaf(10)).build();
        SurchargeRule fragile = SurchargeRule.builder().id(UUID.randomUUID()).name("fragile")
                .conditionExpression("packageType == 'FRAGILE'").surchargeType(SurchargeType.FIXED)
                .value(BigDecimal.valueOf(200)).build();
        return BillingPolicy.builder().id(UUID.randomUUID()).tenantId(UUID.randomUUID()).name("grid")
                .pricingRules(List.of(rule)).surchargeRules(List.of(fragile)).promotions(List.of())
                .loyaltyRules(List.of()).commissionRules(List.of()).specialSurcharges(List.of())
                .hubStorageRules(List.of()).networkTransitRules(List.of())
                .status(PolicyStatus.DRAFT).validFrom(LocalDate.now()).build();
    }

    @Test
    @DisplayName("evaluateWith prices a DRAFT policy; volume and waiting minutes are billed and itemised")
    void volumeAndWaitingAreBilled() {
        PricingContext ctx = PricingContext.builder().distanceKm(10).weightKg(2).volumeCbm(0.5).waitingMinutes(12)
                .packageTypes(List.of(PackageType.STANDARD)).build();

        PriceEvaluation ev = engine.evaluateWith(draftPolicy(), ctx).block();

        // 500 + 10×100 + 2×50 + 0.5×2000 + 12×10
        assertThat(ev.getSellingPrice().getAmount().longValue()).isEqualTo(500 + 1000 + 100 + 1000 + 120);
        assertThat(ev.getPriceBreakdown()).extracting(l -> l.getType())
                .contains(LineItemType.PER_CBM, LineItemType.WAITING);
    }

    @Test
    @DisplayName("without volume or waiting the price is the same as before the extension")
    void absentComponentsChangeNothing() {
        PricingContext ctx = PricingContext.builder().distanceKm(10).weightKg(2)
                .packageTypes(List.of(PackageType.FRAGILE)).build();

        PriceEvaluation ev = engine.evaluateWith(draftPolicy(), ctx).block();

        assertThat(ev.getSellingPrice().getAmount().longValue()).isEqualTo(500 + 1000 + 100 + 200);
    }
}
