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
     * Updates an existing rule, scoped to the caller's tenant. Re-compiles expressions.
     *
     * <p>Audit n°7 · #5 (IDOR) — the existing rule is fetched with
     * {@code findByIdAndTenantId} so a caller cannot mutate another tenant's rule.
     *
     * @param rule     the rule with updated fields
     * @param tenantId the tenant the rule must belong to
     * @return the updated rule
     */
    Mono<DslRule> updateRule(DslRule rule, UUID tenantId);

    /**
     * Retrieves a rule by its identifier, scoped to the caller's tenant.
     * Audit n°7 · #5 (IDOR) — never call without a tenant to check ownership.
     *
     * @param ruleId   the rule UUID
     * @param tenantId the tenant the rule must belong to
     * @return the rule or an empty Mono if not found or owned by a different tenant
     */
    Mono<DslRule> findById(UUID ruleId, UUID tenantId);

    /**
     * Returns all active rules belonging to a given billing policy, scoped to the caller's
     * tenant, ordered by priority ascending.
     *
     * <p>Audit n°7 · #5 (IDOR) — a policyId alone does not prove the caller owns the
     * parent policy, so the tenant is required to scope the query.
     *
     * @param policyId the parent BillingPolicy identifier
     * @param tenantId the tenant the parent policy must belong to
     * @return ordered flux of active DslRules
     */
    Flux<DslRule> findActiveByPolicyId(UUID policyId, UUID tenantId);

    /**
     * Returns all rules (active and inactive) for a policy, scoped to the caller's tenant.
     * Audit n°7 · #5 (IDOR) — see {@link #findActiveByPolicyId}.
     *
     * @param policyId the parent BillingPolicy identifier
     * @param tenantId the tenant the parent policy must belong to
     * @return flux of DslRules
     */
    Flux<DslRule> findAllByPolicyId(UUID policyId, UUID tenantId);

    /**
     * Activates a rule, scoped to the caller's tenant.
     * Audit n°7 · #5 (IDOR) — tenant ownership is verified before mutation.
     *
     * @param ruleId   the rule UUID
     * @param tenantId the tenant the rule must belong to
     * @return the updated rule
     */
    Mono<DslRule> activateRule(UUID ruleId, UUID tenantId);

    /**
     * Deactivates a rule without deleting it, scoped to the caller's tenant.
     * Audit n°7 · #5 (IDOR) — tenant ownership is verified before mutation.
     *
     * @param ruleId   the rule UUID
     * @param tenantId the tenant the rule must belong to
     * @return the updated rule
     */
    Mono<DslRule> deactivateRule(UUID ruleId, UUID tenantId);

    /**
     * Hard-deletes a rule, scoped to the caller's tenant.
     * Audit n°7 · #5 (IDOR) — tenant ownership is verified before deletion.
     *
     * @param ruleId   the rule UUID
     * @param tenantId the tenant the rule must belong to
     * @return completion signal
     */
    Mono<Void> deleteRule(UUID ruleId, UUID tenantId);

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
