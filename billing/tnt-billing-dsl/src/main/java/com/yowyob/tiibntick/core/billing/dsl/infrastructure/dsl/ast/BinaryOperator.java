package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast;

/**
 * All binary operators supported by the TiiBnTick billing DSL.
 *
 * @author MANFOUO Braun
 */
public enum BinaryOperator {

    // Logical
    AND,
    OR,

    // Comparison
    EQ,   // ==
    NEQ,  // !=
    LT,   // <
    LTE,  // <=
    GT,   // >
    GTE   // >=
}
