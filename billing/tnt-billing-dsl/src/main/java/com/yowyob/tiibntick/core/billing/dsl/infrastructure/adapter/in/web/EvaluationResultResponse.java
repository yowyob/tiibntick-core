package com.yowyob.tiibntick.core.billing.dsl.infrastructure.adapter.in.web;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.EvaluationResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * Outbound REST DTO for {@link EvaluationResult}.
 *
 * @author MANFOUO Braun
 */
public record EvaluationResultResponse(
        MoneyDto basePrice,
        MoneyDto perKmTotal,
        MoneyDto perKgTotal,
        List<MoneyDto> surcharges,
        List<MoneyDto> discounts,
        MoneyDto finalPrice,
        int matchedRuleCount,
        List<AppliedRuleSummary> appliedRules
) {
    public record MoneyDto(BigDecimal amount, String currency) {}

    public record AppliedRuleSummary(
            String ruleId,
            String ruleName,
            int priority,
            MoneyDto delta,
            MoneyDto priceAfter
    ) {}

    public static EvaluationResultResponse from(EvaluationResult result) {
        return new EvaluationResultResponse(
                toDto(result.getBasePrice()),
                toDto(result.getPerKmTotal()),
                toDto(result.getPerKgTotal()),
                result.getSurcharges().stream().map(EvaluationResultResponse::toDto).toList(),
                result.getDiscounts().stream().map(EvaluationResultResponse::toDto).toList(),
                toDto(result.getFinalPrice()),
                result.matchedRuleCount(),
                result.getAppliedRules().stream()
                        .map(r -> new AppliedRuleSummary(
                                r.getRuleId() != null ? r.getRuleId().toString() : null,
                                r.getRuleName(),
                                r.getPriority(),
                                toDto(r.getDelta()),
                                toDto(r.getPriceAfter())
                        )).toList()
        );
    }

    private static MoneyDto toDto(com.yowyob.tiibntick.core.billing.dsl.domain.model.Money m) {
        if (m == null) return null;
        return new MoneyDto(m.getAmount(), m.getCurrency().getCurrencyCode());
    }
}
