package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast;

import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslEvaluationException;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AST node for the unary NOT operator.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>Added {@link #getOperand()} accessor for use by
 *       {@link com.yowyob.tiibntick.core.billing.dsl.application.service.DslAccessValidator}.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@RequiredArgsConstructor
@Getter
public class UnaryOpNode extends AstNode {

    /** Currently only NOT is supported. */
    private final String operator;
    private final AstNode operand;

    @Override
    public Object evaluate(PricingContext ctx) {
        Object val = operand.evaluate(ctx);
        if (val instanceof Boolean b) return !b;
        throw new DslEvaluationException("NOT operator requires a boolean operand, got: " + val);
    }

    @Override
    public String nodeType() {
        return "Unary(" + operator + " " + operand.nodeType() + ")";
    }
}
