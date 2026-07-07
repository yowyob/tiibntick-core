package com.yowyob.tiibntick.core.billing.dsl.domain.model;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root representing a single tariff rule written in the TiiBnTick DSL.
 * <p>
 * A rule has:
 * <ul>
 *   <li>A human-readable {@code name} (e.g. "Standard Zone YDE")</li>
 *   <li>A raw DSL {@code conditionExpression} (e.g. {@code "weight <= 5 AND distance <= 10"})</li>
 *   <li>One or more {@link DslAction} objects to apply when the condition matches</li>
 *   <li>A {@code priority} integer: rules are evaluated in ascending order;
 *       lower number = evaluated first</li>
 *   <li>A {@code tenantId} for multi-tenant isolation</li>
 *   <li>A {@code policyId} linking this rule to its parent {@code BillingPolicy}</li>
 * </ul>
 * </p>
 *
 * <p>The {@code conditionExpression} is compiled to an AST by {@code DslCompilerService}
 * and cached as {@code parsedCondition} for repeated fast evaluation.</p>
 *
 * @author MANFOUO Braun
 */
@Value
@Builder(toBuilder = true)
public class DslRule {

    UUID id;

    /** Human-readable identifier of this rule within its policy. */
    String name;

    /** Raw DSL condition text as authored by the agency administrator. */
    String conditionExpression;

    /** Raw DSL action text — parsed into {@code actions} at compile time. */
    String actionExpression;

    /**
     * Compiled action list produced by {@code DslCompilerService}.
     * May be {@code null} if the rule has not yet been compiled.
     */
    List<DslAction> actions;

    /**
     * Evaluation priority: lower value = evaluated first.
     * Rules are sorted before evaluation by the PricingEngine.
     */
    int priority;

    /** Whether this rule is currently active. Inactive rules are skipped. */
    boolean active;

    /** Tenant (agency group) owning this rule. */
    UUID tenantId;

    /** ID of the parent BillingPolicy aggregate. */
    UUID policyId;

    Instant createdAt;

    @With
    Instant updatedAt;

    /** Description shown in the admin UI. */
    String description;

    /**
     * Activates this rule by returning a new instance with {@code active = true}.
     *
     * @return a new activated DslRule
     */
    public DslRule activate() {
        return this.toBuilder().active(true).updatedAt(Instant.now()).build();
    }

    /**
     * Deactivates this rule by returning a new instance with {@code active = false}.
     *
     * @return a new deactivated DslRule
     */
    public DslRule deactivate() {
        return this.toBuilder().active(false).updatedAt(Instant.now()).build();
    }

    /**
     * Returns {@code true} if this rule has a compiled action list.
     */
    public boolean isCompiled() {
        return actions != null && !actions.isEmpty();
    }
}
