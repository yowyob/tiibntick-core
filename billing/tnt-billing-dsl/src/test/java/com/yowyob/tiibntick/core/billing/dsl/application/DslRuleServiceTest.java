package com.yowyob.tiibntick.core.billing.dsl.application;

import com.yowyob.tiibntick.core.billing.dsl.application.service.DslCompilerService;
import com.yowyob.tiibntick.core.billing.dsl.application.service.DslRuleService;
import com.yowyob.tiibntick.core.billing.dsl.application.service.DslValidatorService;
import com.yowyob.tiibntick.core.billing.dsl.application.service.PricingEngine;
import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslRuleNotFoundException;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslRule;
import com.yowyob.tiibntick.core.billing.dsl.domain.port.out.IDslRuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the Audit n°7 · #5 (IDOR) fix on {@link DslRuleService}: every
 * single-rule / per-policy operation must be scoped to the caller's tenant so that a caller
 * cannot read or mutate another tenant's DSL rule (or infer it via a policyId owned by a
 * different tenant).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DslRuleService — tenant scoping (Audit n°7 · #5)")
class DslRuleServiceTest {

    @Mock
    private IDslRuleRepository repository;

    @Mock
    private DslCompilerService compilerService;

    @Mock
    private DslValidatorService validatorService;

    @Mock
    private PricingEngine pricingEngine;

    @InjectMocks
    private DslRuleService dslRuleService;

    private final UUID RULE_ID = UUID.randomUUID();
    private final UUID POLICY_ID = UUID.randomUUID();
    private final UUID TENANT_ID = UUID.randomUUID();
    private final UUID OTHER_TENANT_ID = UUID.randomUUID();

    private DslRule sampleRule() {
        return DslRule.builder()
                .id(RULE_ID)
                .name("Standard")
                .conditionExpression("weight <= 5")
                .actionExpression("setPrice(1000)")
                .priority(10)
                .active(true)
                .tenantId(TENANT_ID)
                .policyId(POLICY_ID)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("findById returns the rule when it belongs to the caller's tenant")
    void testFindByIdSameTenant() {
        when(repository.findByIdAndTenantId(RULE_ID, TENANT_ID)).thenReturn(Mono.just(sampleRule()));

        StepVerifier.create(dslRuleService.findById(RULE_ID, TENANT_ID))
                .expectNextMatches(r -> r.getId().equals(RULE_ID))
                .verifyComplete();
    }

    @Test
    @DisplayName("IDOR: findById must not return another tenant's rule")
    void testFindByIdRejectsCrossTenantAccess() {
        when(repository.findByIdAndTenantId(RULE_ID, OTHER_TENANT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(dslRuleService.findById(RULE_ID, OTHER_TENANT_ID))
                .expectError(DslRuleNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("IDOR: activateRule must not activate another tenant's rule")
    void testActivateRuleRejectsCrossTenantAccess() {
        when(repository.findByIdAndTenantId(RULE_ID, OTHER_TENANT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(dslRuleService.activateRule(RULE_ID, OTHER_TENANT_ID))
                .expectError(DslRuleNotFoundException.class)
                .verify();

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("IDOR: deactivateRule must not deactivate another tenant's rule")
    void testDeactivateRuleRejectsCrossTenantAccess() {
        when(repository.findByIdAndTenantId(RULE_ID, OTHER_TENANT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(dslRuleService.deactivateRule(RULE_ID, OTHER_TENANT_ID))
                .expectError(DslRuleNotFoundException.class)
                .verify();

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("IDOR: deleteRule must not delete another tenant's rule")
    void testDeleteRuleRejectsCrossTenantAccess() {
        when(repository.findByIdAndTenantId(RULE_ID, OTHER_TENANT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(dslRuleService.deleteRule(RULE_ID, OTHER_TENANT_ID))
                .expectError(DslRuleNotFoundException.class)
                .verify();

        verify(repository, never()).deleteById(RULE_ID);
    }

    @Test
    @DisplayName("IDOR: updateRule must not update another tenant's rule")
    void testUpdateRuleRejectsCrossTenantAccess() {
        DslRule updatePayload = sampleRule().toBuilder().name("Renamed").build();
        when(repository.findByIdAndTenantId(RULE_ID, OTHER_TENANT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(dslRuleService.updateRule(updatePayload, OTHER_TENANT_ID))
                .expectError(DslRuleNotFoundException.class)
                .verify();

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("findActiveByPolicyId only returns rules scoped to the caller's tenant")
    void testFindActiveByPolicyIdIsTenantScoped() {
        when(repository.findActiveByPolicyIdAndTenantIdOrderByPriorityAsc(POLICY_ID, TENANT_ID))
                .thenReturn(Flux.just(sampleRule()));
        when(repository.findActiveByPolicyIdAndTenantIdOrderByPriorityAsc(POLICY_ID, OTHER_TENANT_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(dslRuleService.findActiveByPolicyId(POLICY_ID, TENANT_ID))
                .expectNextCount(1)
                .verifyComplete();

        // IDOR: a caller from another tenant supplying the same policyId gets nothing back.
        StepVerifier.create(dslRuleService.findActiveByPolicyId(POLICY_ID, OTHER_TENANT_ID))
                .verifyComplete();
    }

    @Test
    @DisplayName("findAllByPolicyId only returns rules scoped to the caller's tenant")
    void testFindAllByPolicyIdIsTenantScoped() {
        when(repository.findByPolicyIdAndTenantIdOrderByPriorityAsc(POLICY_ID, OTHER_TENANT_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(dslRuleService.findAllByPolicyId(POLICY_ID, OTHER_TENANT_ID))
                .verifyComplete();
    }
}
