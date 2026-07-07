package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslVariableType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AST leaf node holding a constant literal value (number, string or enum name).
 *
 * @author MANFOUO Braun
 */
@RequiredArgsConstructor
@Getter
public class LiteralNode extends AstNode {

    private final Object value;
    private final DslVariableType type;

    @Override
    public Object evaluate(PricingContext ctx) {
        return value;
    }

    @Override
    public String nodeType() {
        return "Literal(" + value + ")";
    }
}
