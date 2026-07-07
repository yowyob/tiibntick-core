package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast;

import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslEvaluationException;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AST node for binary operations: AND, OR, ==, !=, <, <=, >, >=.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>Added {@link #getLeft()}, {@link #getRight()}, {@link #getOperator()} accessors
 *       for use by {@link com.yowyob.tiibntick.core.billing.dsl.application.service.DslAccessValidator}.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@RequiredArgsConstructor
@Getter
public class BinaryOpNode extends AstNode {

    private final AstNode left;
    private final BinaryOperator operator;
    private final AstNode right;

    @Override
    public Object evaluate(PricingContext ctx) {
        return switch (operator) {
            case AND -> asBoolean(left.evaluate(ctx)) && asBoolean(right.evaluate(ctx));
            case OR  -> asBoolean(left.evaluate(ctx)) || asBoolean(right.evaluate(ctx));
            default  -> evaluateComparison(left.evaluate(ctx), operator, right.evaluate(ctx));
        };
    }

    private boolean evaluateComparison(Object leftVal, BinaryOperator op, Object rightVal) {
        if (leftVal == null || rightVal == null) {
            return op == BinaryOperator.EQ
                    ? leftVal == rightVal
                    : op == BinaryOperator.NEQ && leftVal != rightVal;
        }

        // Enum or String equality
        if (!(leftVal instanceof Number) || !(rightVal instanceof Number)) {
            String ls = leftVal.toString();
            String rs = rightVal.toString();
            return switch (op) {
                case EQ  -> ls.equalsIgnoreCase(rs);
                case NEQ -> !ls.equalsIgnoreCase(rs);
                default  -> throw new DslEvaluationException(
                        "Operator " + op + " cannot be applied to non-numeric types: " + ls + ", " + rs);
            };
        }

        // Numeric comparison
        double l = ((Number) leftVal).doubleValue();
        double r = ((Number) rightVal).doubleValue();
        return switch (op) {
            case EQ  -> l == r;
            case NEQ -> l != r;
            case LT  -> l < r;
            case LTE -> l <= r;
            case GT  -> l > r;
            case GTE -> l >= r;
            default  -> throw new DslEvaluationException("Unexpected operator: " + op);
        };
    }

    private boolean asBoolean(Object val) {
        if (val instanceof Boolean b) return b;
        throw new DslEvaluationException("Expected boolean but got: " + val);
    }

    @Override
    public String nodeType() {
        return "BinaryOp(" + left.nodeType() + " " + operator + " " + right.nodeType() + ")";
    }
}
