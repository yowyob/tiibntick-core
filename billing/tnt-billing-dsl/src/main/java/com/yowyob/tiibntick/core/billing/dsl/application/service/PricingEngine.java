package com.yowyob.tiibntick.core.billing.dsl.application.service;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.*;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast.AstNode;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.evaluator.DslEvaluator;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.executor.ActionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core pricing engine that evaluates an ordered list of {@link DslRule} instances
 * against a {@link PricingContext} and produces an {@link EvaluationResult}.
 *
 * <p>Evaluation algorithm:</p>
 * <ol>
 *   <li>Sort rules by {@code priority} ascending (lower = evaluated first)</li>
 *   <li>For each active rule:
 *     <ol>
 *       <li>Look up or compile the condition AST from the local cache</li>
 *       <li>Evaluate the condition; skip the rule if it returns {@code false}</li>
 *       <li>Apply each {@link DslAction} in order to the running price</li>
 *       <li>Record the delta and final price in an {@link AppliedRuleRecord}</li>
 *     </ol>
 *   </li>
 *   <li>Build and return the {@link EvaluationResult} with complete breakdown</li>
 * </ol>
 *
 * <p>The AST cache maps {@code ruleId → AstNode} to avoid re-parsing on every call.
 * The cache is invalidated whenever a rule is recompiled.</p>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingEngine {

    private final DslCompilerService compilerService;
    private final DslEvaluator evaluator;
    private final ActionExecutor actionExecutor;

    /** AST cache: ruleId (as String) → compiled AstNode. Thread-safe. */
    private final Map<String, AstNode> astCache = new ConcurrentHashMap<>();

    /**
     * Evaluates the given rule set against the context.
     *
     * @param rules   list of active DslRules (will be sorted internally)
     * @param ctx     the runtime pricing context
     * @param currency ISO 4217 currency code for the result (e.g. "XAF")
     * @return complete evaluation result
     */
    public EvaluationResult evaluate(List<DslRule> rules, PricingContext ctx, String currency) {
        List<DslRule> sorted = rules.stream()
                .filter(DslRule::isActive)
                .sorted(Comparator.comparingInt(DslRule::getPriority))
                .toList();

        Money runningPrice = Money.zeroXAF();
        if (!currency.equals("XAF")) {
            runningPrice = Money.of(0, currency);
        }

        Money basePrice = runningPrice;
        Money perKmTotal = Money.of(0, currency);
        Money perKgTotal = Money.of(0, currency);
        List<Money> surcharges = new ArrayList<>();
        List<Money> discounts = new ArrayList<>();
        List<AppliedRuleRecord> appliedRules = new ArrayList<>();

        for (DslRule rule : sorted) {
            AstNode ast = getOrCompileAst(rule);
            boolean matches;
            try {
                matches = evaluator.evaluate(ast, ctx);
            } catch (Exception e) {
                log.warn("Rule '{}' condition evaluation failed, skipping: {}", rule.getName(), e.getMessage());
                continue;
            }

            if (!matches) continue;

            log.debug("Rule '{}' matched context for tenant={}", rule.getName(), ctx.getTenantId());

            List<DslAction> actions = rule.getActions();
            Money priceBeforeRule = runningPrice;

            for (DslAction action : actions) {
                Money priceBefore = runningPrice;
                runningPrice = actionExecutor.execute(action, runningPrice, ctx);
                Money delta = actionExecutor.computeDelta(action, priceBefore, runningPrice);

                // Classify delta for breakdown
                switch (action.getActionType()) {
                    case SET_BASE  -> basePrice = runningPrice;
                    case SET_PER_KM -> perKmTotal = perKmTotal.add(
                            Money.of(action.getValue().multiply(
                                    java.math.BigDecimal.valueOf(ctx.getDistanceKm())),
                                    resolveCurrency(action, currency)));
                    case SET_PER_KG -> perKgTotal = perKgTotal.add(
                            Money.of(action.getValue().multiply(
                                    java.math.BigDecimal.valueOf(ctx.getWeightKg())),
                                    resolveCurrency(action, currency)));
                    case ADD_FIXED, ADD_PCT -> surcharges.add(delta.isNegative() ? delta.multiply(-1) : delta);
                    case DISCOUNT_PCT, DISCOUNT_FIXED -> discounts.add(delta.isNegative() ? delta.multiply(-1) : delta);
                    default -> { /* handled */ }
                }
            }

            Money deltaForRule = runningPrice.subtract(priceBeforeRule);
            appliedRules.add(AppliedRuleRecord.builder()
                    .ruleId(rule.getId())
                    .ruleName(rule.getName())
                    .priority(rule.getPriority())
                    .actions(actions)
                    .delta(deltaForRule)
                    .priceAfter(runningPrice)
                    .build());
        }

        return EvaluationResult.builder()
                .basePrice(basePrice)
                .perKmTotal(perKmTotal)
                .perKgTotal(perKgTotal)
                .surcharges(surcharges)
                .discounts(discounts)
                .finalPrice(runningPrice)
                .appliedRules(appliedRules)
                .currencyCode(currency)
                .build();
    }

    /**
     * Invalidates the cached AST for a given rule (called after rule update).
     *
     * @param ruleId the rule identifier whose AST should be evicted
     */
    public void invalidateCache(String ruleId) {
        astCache.remove(ruleId);
    }

    // ─────────────────────────────── HELPERS ─────────────────────────────────

    private AstNode getOrCompileAst(DslRule rule) {
        String cacheKey = rule.getId() != null ? rule.getId().toString() : rule.getName();
        return astCache.computeIfAbsent(cacheKey,
                k -> compilerService.compileCondition(rule.getConditionExpression()));
    }

    private String resolveCurrency(DslAction action, String fallback) {
        return (action.getCurrencyCode() != null && !action.getCurrencyCode().isBlank())
                ? action.getCurrencyCode()
                : fallback;
    }
}
