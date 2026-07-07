package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AST node for the {@code CONTAINS} operator ().
 *
 * <p>Tests whether a list/set variable contains a given element.
 * The variable must resolve to a {@code Set<String>} in {@link PricingContext}.
 *
 * <p>Example DSL expressions:
 * <pre>
 *   activeEquipmentTypes CONTAINS REFRIGERATED_BOX
 *   activeEquipmentTypes CONTAINS GPS_TRACKER
 * </pre>
 *
 * <p>Evaluation is case-insensitive.
 *
 * @author MANFOUO Braun
 */
@RequiredArgsConstructor
@Getter
public class ContainsNode extends AstNode {

    /** The list-typed variable (e.g. {@code activeEquipmentTypes}). */
    private final VariableNode listVariable;

    /** The element to search for (e.g. {@code REFRIGERATED_BOX}). */
    private final LiteralNode element;

    @Override
    public Object evaluate(PricingContext ctx) {
        Object collectionValue = listVariable.evaluate(ctx);
        if (collectionValue == null) return false;

        String target = element.getValue().toString().toUpperCase();

        // Handle Set<String>
        if (collectionValue instanceof java.util.Set<?> set) {
            return set.stream()
                    .map(Object::toString)
                    .map(String::toUpperCase)
                    .anyMatch(target::equals);
        }

        // Handle comma-separated string fallback
        if (collectionValue instanceof String csv) {
            for (String part : csv.split(",")) {
                if (part.trim().equalsIgnoreCase(target)) return true;
            }
            return false;
        }

        // Single-value comparison
        return collectionValue.toString().equalsIgnoreCase(target);
    }

    /**
     * Returns the name of the list variable for use in error messages and validators.
     */
    public String getListVariableName() {
        return listVariable.getVariableName();
    }

    @Override
    public String nodeType() {
        return "Contains(" + listVariable.getVariableName()
                + " CONTAINS " + element.getValue() + ")";
    }
}
