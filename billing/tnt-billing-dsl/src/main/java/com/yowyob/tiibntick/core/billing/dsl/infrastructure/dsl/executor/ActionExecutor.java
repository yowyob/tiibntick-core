package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.executor;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAction;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;

import java.math.BigDecimal;

/**
 * Applies a {@link DslAction} to the running price during a pricing evaluation pass.
 * <p>
 * The executor is stateless; it uses the supplied {@code currentPrice} and returns a
 * new {@link Money} instance representing the price after applying the action.
 * It does NOT mutate any field.
 * </p>
 *
 * @author MANFOUO Braun
 */
public class ActionExecutor {

    /**
     * Applies the action to {@code currentPrice} and returns the updated price.
     *
     * @param action       the action to apply
     * @param currentPrice the price before this action
     * @param ctx          the runtime context (used to read distance/weight for rate-based actions)
     * @return the new price after applying the action
     */
    public Money execute(DslAction action, Money currentPrice, PricingContext ctx) {
        String currency = resolveCurrency(action, currentPrice);
        return switch (action.getActionType()) {
            case SET_BASE       -> Money.of(action.getValue(), currency);
            case ADD_FIXED      -> currentPrice.add(Money.of(action.getValue(), currency));
            case ADD_PCT        -> currentPrice.add(currentPrice.percentage(action.getValue()));
            case DISCOUNT_PCT   -> {
                Money discount = currentPrice.percentage(action.getValue());
                yield currentPrice.subtract(discount);
            }
            case DISCOUNT_FIXED -> currentPrice.subtract(Money.of(action.getValue(), currency));
            case SET_PER_KM     -> {
                BigDecimal distanceFee = action.getValue()
                        .multiply(BigDecimal.valueOf(ctx.getDistanceKm()));
                yield currentPrice.add(Money.of(distanceFee, currency));
            }
            case SET_PER_KG     -> {
                BigDecimal weightFee = action.getValue()
                        .multiply(BigDecimal.valueOf(ctx.getWeightKg()));
                yield currentPrice.add(Money.of(weightFee, currency));
            }
        };
    }

    /**
     * Computes the delta (change) applied by an action to the current price.
     * Used for audit trail recording in {@link EvaluationResult}.
     *
     * @param action       the action
     * @param priceBefore  price before action
     * @param priceAfter   price after action
     * @return the net delta (positive = surcharge, negative = discount)
     */
    public Money computeDelta(DslAction action, Money priceBefore, Money priceAfter) {
        return priceAfter.subtract(priceBefore);
    }

    private String resolveCurrency(DslAction action, Money fallback) {
        if (action.getCurrencyCode() != null && !action.getCurrencyCode().isBlank()) {
            return action.getCurrencyCode();
        }
        return fallback.getCurrency().getCurrencyCode();
    }
}
