package com.yowyob.tiibntick.core.billing.dsl.domain.exception;

/**
 * Thrown when an AST node cannot be evaluated against a given {@code PricingContext}.
 *
 * @author MANFOUO Braun
 */
public class DslEvaluationException extends RuntimeException {

    public DslEvaluationException(String message) {
        super(message);
    }

    public DslEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
