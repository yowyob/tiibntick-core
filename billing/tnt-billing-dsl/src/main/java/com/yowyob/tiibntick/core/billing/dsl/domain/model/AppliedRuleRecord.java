package com.yowyob.tiibntick.core.billing.dsl.domain.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

/**
 * Immutable record capturing a rule that matched during a pricing evaluation pass.
 * Used to build the detailed breakdown in {@link EvaluationResult}.
 *
 * @author MANFOUO Braun
 */
@Value
@Builder
public class AppliedRuleRecord {

    UUID ruleId;
    String ruleName;
    int priority;
    List<DslAction> actions;

    /** Net monetary delta applied to the running price by this rule. */
    Money delta;

    /** Running price after this rule was applied. */
    Money priceAfter;
}
