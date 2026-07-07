package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * AST node for the {@code IN} operator.
 *
 * <p>Example: {@code packageType IN [FRAGILE, ELECTRONICS]}</p>
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
public class InListNode extends AstNode {

    private final VariableNode variable;
    private final List<LiteralNode> candidates;

    @Override
    public Object evaluate(PricingContext ctx) {
        Object varValue = variable.evaluate(ctx);
        if (varValue == null) return false;
        String normalized = varValue.toString().toUpperCase();
        return candidates.stream()
                .map(c -> c.getValue().toString().toUpperCase())
                .anyMatch(normalized::equals);
    }

    @Override
    public String nodeType() {
        return "InList(" + variable.getVariableName() + " IN " + candidates + ")";
    }
}
