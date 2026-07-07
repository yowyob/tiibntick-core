package com.yowyob.tiibntick.core.billing.pricing.infrastructure.adapter.in.web;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.PriceEvaluation;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PriceLineItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PriceEvaluationResponse(
        MoneyDto sellingPrice,
        List<LineItemDto> breakdown,
        UUID appliedRuleId,
        List<UUID> appliedSurchargeIds,
        List<UUID> appliedPromotionIds,
        MoneyDto discountApplied,
        MoneyDto platformFee,
        MoneyDto delivererCommission,
        boolean marginNegative,
        Instant computedAt
) {
    public record MoneyDto(BigDecimal amount, String currency) {}

    public record LineItemDto(String label, String type, BigDecimal amount,
                               String currency, boolean isDiscount) {}

    public static PriceEvaluationResponse from(PriceEvaluation e) {
        return new PriceEvaluationResponse(
                toDto(e.getSellingPrice()),
                e.getPriceBreakdown().stream().map(PriceEvaluationResponse::toLine).toList(),
                e.getAppliedRuleId(),
                e.getAppliedSurchargeIds(),
                e.getAppliedPromotionIds(),
                toDto(e.getDiscountApplied()),
                toDto(e.getPlatformFee()),
                toDto(e.getDelivererCommission()),
                e.isMarginNegative(),
                e.getComputedAt()
        );
    }

    private static MoneyDto toDto(com.yowyob.tiibntick.core.billing.dsl.domain.model.Money m) {
        if (m == null) return null;
        return new MoneyDto(m.getAmount(), m.getCurrency().getCurrencyCode());
    }

    private static LineItemDto toLine(PriceLineItem item) {
        return new LineItemDto(
                item.getLabel(),
                item.getType().name(),
                item.getAmount().getAmount(),
                item.getAmount().getCurrency().getCurrencyCode(),
                item.isDiscount()
        );
    }
}
