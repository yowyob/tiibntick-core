package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast;

import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslEvaluationException;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import lombok.RequiredArgsConstructor;

import java.time.LocalTime;

/**
 * AST node for the {@code TIME_IS_BETWEEN} operator ().
 *
 * <p>Tests whether the {@code timeOfDay} in the {@link PricingContext} falls within
 * a given time range. Supports overnight ranges where the end time is before
 * the start time (e.g. 22:00 to 06:00 spans midnight).
 *
 * <p>Example DSL expressions:
 * <pre>
 *   timeOfDay TIME_IS_BETWEEN 22:00 AND 06:00   (night delivery: 22h–06h, crosses midnight)
 *   timeOfDay TIME_IS_BETWEEN 08:00 AND 18:00   (business hours, same day)
 *   timeOfDay TIME_IS_BETWEEN 12:00 AND 14:00   (lunch rush)
 * </pre>
 *
 * @author MANFOUO Braun
 */
@RequiredArgsConstructor
public class TimeIsBetweenNode extends AstNode {

    /** The variable (should be {@code timeOfDay}). */
    private final VariableNode variable;

    /** Start of the time range (inclusive). */
    private final LocalTime start;

    /** End of the time range (inclusive). */
    private final LocalTime end;

    @Override
    public Object evaluate(PricingContext ctx) {
        Object varValue = variable.evaluate(ctx);
        if (varValue == null) return false;

        LocalTime time;
        if (varValue instanceof LocalTime lt) {
            time = lt;
        } else {
            throw new DslEvaluationException(
                    "TIME_IS_BETWEEN requires a LocalTime variable, got: " + varValue.getClass().getSimpleName());
        }

        // Overnight range (e.g. 22:00 to 06:00 wraps past midnight)
        if (start.isAfter(end)) {
            // time >= start OR time <= end
            return !time.isBefore(start) || !time.isAfter(end);
        }
        // Normal same-day range (e.g. 08:00 to 18:00)
        return !time.isBefore(start) && !time.isAfter(end);
    }

    @Override
    public String nodeType() {
        return "TimeIsBetween(" + variable.getVariableName()
                + " TIME_IS_BETWEEN " + start + " AND " + end + ")";
    }

    public LocalTime getStart() { return start; }
    public LocalTime getEnd() { return end; }
}
