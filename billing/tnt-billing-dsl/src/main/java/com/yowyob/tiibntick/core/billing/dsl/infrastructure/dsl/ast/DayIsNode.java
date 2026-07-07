package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.DayOfWeek;

/**
 * AST node for the {@code DAY_IS} operator ().
 *
 * <p>Tests the classification of the delivery day.
 *
 * <p>Supported day classification values:
 * <ul>
 *   <li>{@code WEEKEND}    — Saturday or Sunday</li>
 *   <li>{@code WEEKDAY}    — Monday through Friday</li>
 *   <li>{@code HOLIDAY}    — Public holiday</li>
 *   <li>{@code MONDAY}…{@code SUNDAY} — specific day</li>
 *   <li>{@code NON_WORKING} — weekend OR public holiday</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@RequiredArgsConstructor
@Getter
public class DayIsNode extends AstNode {

    private final VariableNode variable;
    private final String dayClassification;

    @Override
    public Object evaluate(PricingContext ctx) {
        String upperClass = dayClassification.toUpperCase();
        return switch (upperClass) {
            case "WEEKEND"      -> ctx.isWeekend();
            case "WEEKDAY"      -> ctx.isWeekday();
            case "HOLIDAY"      -> Boolean.TRUE.equals(ctx.getIsPublicHoliday());
            case "NON_WORKING"  -> ctx.isNonWorkingDay();
            default -> {
                if (ctx.getDayOfWeek() == null) yield false;
                try {
                    DayOfWeek target = DayOfWeek.valueOf(upperClass);
                    yield ctx.getDayOfWeek() == target;
                } catch (IllegalArgumentException e) {
                    yield false;
                }
            }
        };
    }

    @Override
    public String nodeType() {
        return "DayIs(" + variable.getVariableName() + " DAY_IS " + dayClassification + ")";
    }
}
