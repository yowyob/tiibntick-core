package com.yowyob.tiibntick.core.billing.dsl.application.service;

import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslParseException;
import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslRuleNotFoundException;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.*;
import com.yowyob.tiibntick.core.billing.dsl.domain.port.in.IDslRuleUseCase;
import com.yowyob.tiibntick.core.billing.dsl.domain.port.out.IDslRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application service implementing {@link IDslRuleUseCase}.
 * Orchestrates validation, compilation, persistence and pricing evaluation.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@link #validateExpressionWithAccessLevel} — validates with access level check.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DslRuleService implements IDslRuleUseCase {

    private static final String DEFAULT_CURRENCY = "XAF";

    private final IDslRuleRepository repository;
    private final DslCompilerService compilerService;
    private final DslValidatorService validatorService;
    private final PricingEngine pricingEngine;

    @Override
    public Mono<DslRule> createRule(DslRule rule) {
        return Mono.fromCallable(() -> {
                    List<ValidationError> errors = validatorService.validateRule(rule);
                    if (!errors.isEmpty()) {
                        throw new DslParseException("Rule validation failed: "
                                + errors.get(0).getMessage());
                    }
                    DslRule compiled = compilerService.compile(rule);
                    return compiled.getId() != null ? compiled
                            : compiled.toBuilder().id(UUID.randomUUID())
                                      .createdAt(Instant.now()).build();
                })
                .flatMap(repository::save)
                .doOnSuccess(r -> log.info("Created DslRule '{}' (id={})", r.getName(), r.getId()));
    }

    @Override
    public Mono<DslRule> updateRule(DslRule rule, UUID tenantId) {
        return repository.findByIdAndTenantId(rule.getId(), tenantId)
                .switchIfEmpty(Mono.error(new DslRuleNotFoundException(rule.getId())))
                .flatMap(existing -> Mono.fromCallable(() -> {
                    List<ValidationError> errors = validatorService.validateRule(rule);
                    if (!errors.isEmpty()) {
                        throw new DslParseException("Rule validation failed: "
                                + errors.get(0).getMessage());
                    }
                    pricingEngine.invalidateCache(rule.getId().toString());
                    DslRule compiled = compilerService.compile(rule);
                    return compiled.toBuilder().createdAt(existing.getCreatedAt()).build();
                }))
                .flatMap(repository::save)
                .doOnSuccess(r -> log.info("Updated DslRule '{}' (id={})", r.getName(), r.getId()));
    }

    @Override
    public Mono<DslRule> findById(UUID ruleId, UUID tenantId) {
        return repository.findByIdAndTenantId(ruleId, tenantId)
                .switchIfEmpty(Mono.error(new DslRuleNotFoundException(ruleId)));
    }

    @Override
    public Flux<DslRule> findActiveByPolicyId(UUID policyId, UUID tenantId) {
        return repository.findActiveByPolicyIdAndTenantIdOrderByPriorityAsc(policyId, tenantId);
    }

    @Override
    public Flux<DslRule> findAllByPolicyId(UUID policyId, UUID tenantId) {
        return repository.findByPolicyIdAndTenantIdOrderByPriorityAsc(policyId, tenantId);
    }

    @Override
    public Mono<DslRule> activateRule(UUID ruleId, UUID tenantId) {
        return repository.findByIdAndTenantId(ruleId, tenantId)
                .switchIfEmpty(Mono.error(new DslRuleNotFoundException(ruleId)))
                .map(DslRule::activate)
                .flatMap(repository::save);
    }

    @Override
    public Mono<DslRule> deactivateRule(UUID ruleId, UUID tenantId) {
        return repository.findByIdAndTenantId(ruleId, tenantId)
                .switchIfEmpty(Mono.error(new DslRuleNotFoundException(ruleId)))
                .map(DslRule::deactivate)
                .flatMap(repository::save);
    }

    @Override
    public Mono<Void> deleteRule(UUID ruleId, UUID tenantId) {
        return repository.findByIdAndTenantId(ruleId, tenantId)
                .switchIfEmpty(Mono.error(new DslRuleNotFoundException(ruleId)))
                .flatMap(rule -> {
                    pricingEngine.invalidateCache(ruleId.toString());
                    return repository.deleteById(ruleId);
                });
    }

    @Override
    public Mono<List<ValidationError>> validateExpression(String expression) {
        return Mono.fromCallable(() -> validatorService.validateCondition(expression));
    }

    /**
     * {@inheritDoc}
     *  — validates against an explicit access level.
     */
    @Override
    public Mono<List<ValidationError>> validateExpressionWithAccessLevel(
            String expression, DslAccessLevel accessLevel) {
        return Mono.fromCallable(() ->
                validatorService.validateConditionWithAccessLevel(expression, accessLevel));
    }

    @Override
    public Mono<EvaluationResult> evaluate(UUID policyId, PricingContext ctx) {
        // Audit n°7 · #5 (IDOR) — PricingContext already carries the caller's tenantId;
        // scope the rule lookup with it so a caller cannot evaluate (and thereby infer)
        // another tenant's DSL rules by supplying an arbitrary policyId.
        return repository.findActiveByPolicyIdAndTenantIdOrderByPriorityAsc(policyId, ctx.getTenantId())
                .collectList()
                .flatMap(rules -> Mono.fromCallable(() ->
                        pricingEngine.evaluate(rules, ctx, DEFAULT_CURRENCY)))
                .doOnSuccess(result -> log.debug(
                        "Evaluation for policyId={} finalPrice={} appliedRules={}",
                        policyId, result.getFinalPrice(), result.matchedRuleCount()));
    }
}
