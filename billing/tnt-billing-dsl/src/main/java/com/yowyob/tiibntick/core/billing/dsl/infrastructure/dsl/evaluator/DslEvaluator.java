package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.evaluator;

import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslEvaluationException;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast.AstNode;

/**
 * Evaluates a compiled {@link AstNode} tree against a {@link PricingContext}
 * and returns the boolean result of the condition.
 * <p>
 * The evaluator is intentionally stateless; each call to {@link #evaluate} is
 * independent and thread-safe.
 * </p>
 *
 * @author MANFOUO Braun
 */
public class DslEvaluator {

    /**
     * Evaluates the condition AST against the given context.
     *
     * @param root the root AstNode of the compiled condition
     * @param ctx  the runtime pricing context
     * @return {@code true} if the condition holds for this context
     * @throws DslEvaluationException if the AST contains a type mismatch or unknown variable
     */
    public boolean evaluate(AstNode root, PricingContext ctx) {
        Object result = root.evaluate(ctx);
        if (result instanceof Boolean b) return b;
        throw new DslEvaluationException(
                "DSL condition must evaluate to Boolean, but got: " + result + " [" +
                        (result != null ? result.getClass().getSimpleName() : "null") + "]");
    }
}
