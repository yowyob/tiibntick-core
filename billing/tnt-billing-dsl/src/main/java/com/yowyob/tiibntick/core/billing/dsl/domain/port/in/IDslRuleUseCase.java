package com.yowyob.tiibntick.core.billing.dsl.domain.port.in;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAccessLevel;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslRule;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.EvaluationResult;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.ValidationError;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Inbound port (primary / driving) for the DSL billing module.
 *
 * <p>Implementations are the entry points consumed by:
 * <ul>
 *   <li>{@code tnt-billing-pricing} — to evaluate all rules in a policy</li>
 *   <li>REST adapters — for admin CRUD and on-demand evaluation</li>
 * </ul>
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@link #validateExpressionWithAccessLevel} — validates a raw DSL condition
 *       against a specific {@link DslAccessLevel}.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public interface IDslRuleUseCase {

    /**
     * Creates a new DSL rule after validating and compiling its expressions.
     *
     * @param rule the rule to persist (id may be null; will be generated)
     * @return the persisted rule including compiled actions
     */
    Mono<DslRule> createRule(DslRule rule);

    /**
     * Updates an existing rule. Re-compiles expressions.
     *
     * @param rule the rule with updated fields
     * @return the updated rule
     */
    Mono<DslRule> updateRule(DslRule rule);

    /**
     * Retrieves a rule by its identifier.
     *
     * @param ruleId the rule UUID
     * @return the rule or an empty Mono if not found
     */
    Mono<DslRule> findById(UUID ruleId);

    /**
     * Returns all active rules belonging to a given billing policy,
     * ordered by priority ascending.
     *
     * @param policyId the parent BillingPolicy identifier
     * @return ordered flux of active DslRules
     */
    Flux<DslRule> findActiveByPolicyId(UUID policyId);

    /**
     * Returns all rules (active and inactive) for a policy.
     *
     * @param policyId the parent BillingPolicy identifier
     * @return flux of DslRules
     */
    Flux<DslRule> findAllByPolicyId(UUID policyId);

    /**
     * Activates a rule.
     *
     * @param ruleId the rule UUID
     * @return the updated rule
     */
    Mono<DslRule> activateRule(UUID ruleId);

    /**
     * Deactivates a rule without deleting it.
     *
     * @param ruleId the rule UUID
     * @return the updated rule
     */
    Mono<DslRule> deactivateRule(UUID ruleId);

    /**
     * Hard-deletes a rule.
     *
     * @param ruleId the rule UUID
     * @return completion signal
     */
    Mono<Void> deleteRule(UUID ruleId);

    /**
     * Validates a raw DSL condition expression without persisting.
     * Returns an empty list if valid, otherwise returns all detected errors.
     * No access level restriction applied.
     *
     * @param expression the raw DSL text
     * @return list of validation errors (empty = valid)
     */
    Mono<List<ValidationError>> validateExpression(String expression);

    /**
     *  — Validates a raw DSL condition expression against a specific access level.
     *
     * <p>Returns syntax errors AND access-level restriction violations.
     * For example, a SIMPLIFIED user referencing {@code activeEquipmentTypes}
     * will get an error.
     *
     * @param expression  the raw DSL text
     * @param accessLevel the access level to enforce
     * @return list of validation errors (empty = valid for the given level)
     */
    Mono<List<ValidationError>> validateExpressionWithAccessLevel(String expression,
                                                                   DslAccessLevel accessLevel);

    /**
     * Evaluates all active rules of a policy against the given context.
     * This is the core pricing entry point used by {@code tnt-billing-pricing}.
     *
     * @param policyId the parent BillingPolicy identifier
     * @param context  the runtime pricing context
     * @return complete evaluation result with final price and audit trail
     */
    Mono<EvaluationResult> evaluate(UUID policyId, PricingContext context);
}
