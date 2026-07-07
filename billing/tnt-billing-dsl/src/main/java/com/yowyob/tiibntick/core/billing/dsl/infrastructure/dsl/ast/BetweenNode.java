package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast;

import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslEvaluationException;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AST node for the {@code BETWEEN} operator.
 *
 * <p>Example: {@code distance BETWEEN 5 AND 15}</p>
 * Evaluates to {@code true} when {@code lower <= variable <= upper}.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>Added {@link #getVariable()} accessor for
 *       {@link com.yowyob.tiibntick.core.billing.dsl.application.service.DslAccessValidator}.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@RequiredArgsConstructor
@Getter
public class BetweenNode extends AstNode {

    private final VariableNode variable;
    private final LiteralNode lower;
    private final LiteralNode upper;

    @Override
    public Object evaluate(PricingContext ctx) {
        Object varVal = variable.evaluate(ctx);
        if (!(varVal instanceof Number n)) {
            throw new DslEvaluationException(
                    "BETWEEN requires a numeric variable, got: " + varVal);
        }
        double val = n.doubleValue();
        double lo  = ((Number) lower.getValue()).doubleValue();
        double hi  = ((Number) upper.getValue()).doubleValue();
        return val >= lo && val <= hi;
    }

    @Override
    public String nodeType() {
        return "Between(" + variable.getVariableName()
                + " BETWEEN " + lower.getValue() + " AND " + upper.getValue() + ")";
    }
}
