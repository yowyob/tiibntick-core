package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;

/**
 * Abstract base class for every node in the DSL Abstract Syntax Tree (AST).
 * <p>
 * The DSL expression is parsed into a tree of {@code AstNode} instances.
 * Evaluation is performed by calling {@link #evaluate(PricingContext)} on the root node,
 * which recursively evaluates its children and returns the result typed as {@code Object}.
 * </p>
 *
 * <p>Supported concrete types:</p>
 * <ul>
 *   <li>{@link BinaryOpNode}  — logical (AND, OR) and comparison operators</li>
 *   <li>{@link UnaryOpNode}   — NOT operator</li>
 *   <li>{@link VariableNode}  — reads a named variable from {@link PricingContext}</li>
 *   <li>{@link LiteralNode}   — holds a constant value (number, string, enum)</li>
 *   <li>{@link InListNode}    — variable IN [v1, v2, v3]</li>
 *   <li>{@link BetweenNode}   — variable BETWEEN low AND high</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public abstract class AstNode {

    /**
     * Evaluates this node against the supplied pricing context.
     *
     * @param ctx the runtime pricing context
     * @return evaluation result — typically {@code Boolean} for condition nodes,
     *         {@code Object} (Number, String, Enum) for value nodes
     */
    public abstract Object evaluate(PricingContext ctx);

    /**
     * Returns a compact string representation of this node (useful for debugging).
     */
    public abstract String nodeType();

    @Override
    public String toString() {
        return nodeType();
    }
}
