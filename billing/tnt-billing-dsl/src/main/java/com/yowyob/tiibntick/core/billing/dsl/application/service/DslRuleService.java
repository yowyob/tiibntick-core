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
    public Mono<DslRule> updateRule(DslRule rule) {
        return repository.findById(rule.getId())
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
    public Mono<DslRule> findById(UUID ruleId) {
        return repository.findById(ruleId)
                .switchIfEmpty(Mono.error(new DslRuleNotFoundException(ruleId)));
    }

    @Override
    public Flux<DslRule> findActiveByPolicyId(UUID policyId) {
        return repository.findActiveByPolicyIdOrderByPriorityAsc(policyId);
    }

    @Override
    public Flux<DslRule> findAllByPolicyId(UUID policyId) {
        return repository.findByPolicyIdOrderByPriorityAsc(policyId);
    }

    @Override
    public Mono<DslRule> activateRule(UUID ruleId) {
        return repository.findById(ruleId)
                .switchIfEmpty(Mono.error(new DslRuleNotFoundException(ruleId)))
                .map(DslRule::activate)
                .flatMap(repository::save);
    }

    @Override
    public Mono<DslRule> deactivateRule(UUID ruleId) {
        return repository.findById(ruleId)
                .switchIfEmpty(Mono.error(new DslRuleNotFoundException(ruleId)))
                .map(DslRule::deactivate)
                .flatMap(repository::save);
    }

    @Override
    public Mono<Void> deleteRule(UUID ruleId) {
        return repository.findById(ruleId)
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
        return repository.findActiveByPolicyIdOrderByPriorityAsc(policyId)
                .collectList()
                .flatMap(rules -> Mono.fromCallable(() ->
                        pricingEngine.evaluate(rules, ctx, DEFAULT_CURRENCY)))
                .doOnSuccess(result -> log.debug(
                        "Evaluation for policyId={} finalPrice={} appliedRules={}",
                        policyId, result.getFinalPrice(), result.matchedRuleCount()));
    }
}
