package com.yowyob.tiibntick.core.billing.dsl.domain.model;

/**
 * Type tags for DSL expressions used during parsing and evaluation.
 * The evaluator uses these to correctly compare operands and resolve
 * variables from the {@link PricingContext}.
 *
 * @author MANFOUO Braun
 */
public enum DslVariableType {
    NUMBER,
    STRING,
    BOOLEAN,
    ENUM,
    TIME,
    LIST
}
