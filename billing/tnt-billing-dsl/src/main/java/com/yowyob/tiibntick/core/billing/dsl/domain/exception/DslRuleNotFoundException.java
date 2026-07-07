package com.yowyob.tiibntick.core.billing.dsl.domain.exception;

import java.util.UUID;

/**
 * Thrown when a {@code DslRule} cannot be found by its identifier.
 *
 * @author MANFOUO Braun
 */
public class DslRuleNotFoundException extends RuntimeException {

    public DslRuleNotFoundException(UUID ruleId) {
        super("DslRule not found: " + ruleId);
    }

    public DslRuleNotFoundException(String message) {
        super(message);
    }
}
