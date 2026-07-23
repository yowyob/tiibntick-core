package com.yowyob.tiibntick.core.billing.pricing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.core.billing.pricing.application.service.BillingPolicyService;
import com.yowyob.tiibntick.core.billing.pricing.domain.exception.BillingPolicyNotFoundException;
import com.yowyob.tiibntick.core.billing.pricing.domain.exception.InvalidPolicyException;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PricingRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.BillingPolicyAnchorPayload;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.BillingPolicyAnchorPort;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.IBillingPolicyRepository;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BillingPolicyService")
class BillingPolicyServiceTest {

    @Mock
    private IBillingPolicyRepository policyRepository;

    @Mock
    private BillingPolicyAnchorPort billingPolicyAnchorPort;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private BillingPolicyService policyService;

    private final UUID TENANT_ID = UUID.randomUUID();
    private final UUID OTHER_TENANT_ID = UUID.randomUUID();
    private final UUID POLICY_ID = UUID.randomUUID();

    private BillingPolicy samplePolicy() {
        PricingRule rule = PricingRule.builder()
                .id(UUID.randomUUID())
                .name("Standard YDE")
                .conditionExpression("weight <= 5 AND distance <= 10")
                .basePrice(Money.of(1000L, "XAF"))
                .perKmRate(Money.of(50L, "XAF"))
                .priority(10)
                .build();

        return BillingPolicy.builder()
                .id(POLICY_ID)
                .tenantId(TENANT_ID)
                .name("Standard Yaoundé")
                .pricingRules(List.of(rule))
                .surchargeRules(List.of())
                .promotions(List.of())
                .loyaltyRules(List.of())
                .commissionRules(List.of())
                .isDefault(true)
                .status(PolicyStatus.DRAFT)
                .validFrom(LocalDate.now())
                .build();
    }

    @Test
    @DisplayName("createPolicy should assign id and set status to DRAFT")
    void testCreatePolicy() {
        BillingPolicy policy = samplePolicy();
        when(policyRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(policyService.createPolicy(policy))
                .expectNextMatches(p -> p.getStatus() == PolicyStatus.DRAFT
                        && p.getId() != null)
                .verifyComplete();
    }

    @Test
    @DisplayName("createPolicy should fail when no pricingRules provided")
    void testCreatePolicyNoRules() {
        BillingPolicy noRules = BillingPolicy.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .name("Empty")
                .pricingRules(List.of())
                .validFrom(LocalDate.now())
                .status(PolicyStatus.DRAFT)
                .build();

        StepVerifier.create(policyService.createPolicy(noRules))
                .expectError(InvalidPolicyException.class)
                .verify();
    }

    @Test
    @DisplayName("activatePolicy should transition DRAFT → ACTIVE")
    void testActivatePolicy() {
        BillingPolicy draftPolicy = samplePolicy();
        BillingPolicy activePolicy = draftPolicy.activate();

        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(Mono.just(draftPolicy));
        when(policyRepository.save(any())).thenReturn(Mono.just(activePolicy));
        when(billingPolicyAnchorPort.anchor(any())).thenReturn(Mono.empty());

        StepVerifier.create(policyService.activatePolicy(POLICY_ID, TENANT_ID))
                .expectNextMatches(p -> p.getStatus() == PolicyStatus.ACTIVE)
                .verifyComplete();
    }

    @Test
    @DisplayName("activatePolicy should anchor the activation on-chain with the policy's identifiers")
    void testActivatePolicyAnchorsOnChain() {
        BillingPolicy draftPolicy = samplePolicy().toBuilder().ownerActorId("agency-42").build();
        BillingPolicy activePolicy = draftPolicy.activate();

        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(Mono.just(draftPolicy));
        when(policyRepository.save(any())).thenReturn(Mono.just(activePolicy));
        when(billingPolicyAnchorPort.anchor(any())).thenReturn(Mono.empty());

        StepVerifier.create(policyService.activatePolicy(POLICY_ID, TENANT_ID))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<BillingPolicyAnchorPayload> captor = ArgumentCaptor.forClass(BillingPolicyAnchorPayload.class);
        verify(billingPolicyAnchorPort).anchor(captor.capture());
        BillingPolicyAnchorPayload payload = captor.getValue();

        assertThat(payload.tenantId()).isEqualTo(TENANT_ID);
        assertThat(payload.policyId()).isEqualTo(POLICY_ID);
        assertThat(payload.ownerActorId()).isEqualTo("agency-42");
        assertThat(payload.policySummaryJson()).contains("\"pricingRuleCount\":1");
    }

    @Test
    @DisplayName("activatePolicy should still succeed when on-chain anchoring fails")
    void testActivatePolicySucceedsWhenAnchoringFails() {
        BillingPolicy draftPolicy = samplePolicy();
        BillingPolicy activePolicy = draftPolicy.activate();

        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(Mono.just(draftPolicy));
        when(policyRepository.save(any())).thenReturn(Mono.just(activePolicy));
        when(billingPolicyAnchorPort.anchor(any())).thenReturn(Mono.error(new RuntimeException("trust unavailable")));

        StepVerifier.create(policyService.activatePolicy(POLICY_ID, TENANT_ID))
                .expectNextMatches(p -> p.getStatus() == PolicyStatus.ACTIVE)
                .verifyComplete();
    }

    @Test
    @DisplayName("activatePolicy should fail when policy not found")
    void testActivatePolicyNotFound() {
        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(policyService.activatePolicy(POLICY_ID, TENANT_ID))
                .expectError(BillingPolicyNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("IDOR (Audit n°7 · #5): activatePolicy must not activate another tenant's policy")
    void testActivatePolicyRejectsCrossTenantAccess() {
        // The policy exists (tenant TENANT_ID) but the caller authenticates as OTHER_TENANT_ID.
        // The repository call is tenant-scoped, so it must come back empty for the wrong tenant,
        // never leaking or mutating another tenant's policy.
        when(policyRepository.findByIdAndTenantId(POLICY_ID, OTHER_TENANT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(policyService.activatePolicy(POLICY_ID, OTHER_TENANT_ID))
                .expectError(BillingPolicyNotFoundException.class)
                .verify();

        verify(policyRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("IDOR (Audit n°7 · #5): findById must not return another tenant's policy")
    void testFindByIdRejectsCrossTenantAccess() {
        when(policyRepository.findByIdAndTenantId(POLICY_ID, OTHER_TENANT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(policyService.findById(POLICY_ID, OTHER_TENANT_ID))
                .expectError(BillingPolicyNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("deactivatePolicy should transition ACTIVE → INACTIVE")
    void testDeactivatePolicy() {
        BillingPolicy activePolicy = samplePolicy().activate();
        BillingPolicy inactivePolicy = activePolicy.deactivate();

        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(Mono.just(activePolicy));
        when(policyRepository.save(any())).thenReturn(Mono.just(inactivePolicy));

        StepVerifier.create(policyService.deactivatePolicy(POLICY_ID, TENANT_ID))
                .expectNextMatches(p -> p.getStatus() == PolicyStatus.INACTIVE)
                .verifyComplete();
    }

    @Test
    @DisplayName("findActiveByTenantId should return Flux of active policies")
    void testFindActiveByTenantId() {
        BillingPolicy active = samplePolicy().activate();
        when(policyRepository.findActiveByTenantId(TENANT_ID)).thenReturn(Flux.just(active));

        StepVerifier.create(policyService.findActiveByTenantId(TENANT_ID))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("deletePolicy should complete when policy exists")
    void testDeletePolicy() {
        BillingPolicy policy = samplePolicy();
        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(Mono.just(policy));
        when(policyRepository.deleteById(POLICY_ID)).thenReturn(Mono.empty());

        StepVerifier.create(policyService.deletePolicy(POLICY_ID, TENANT_ID))
                .verifyComplete();
    }

    @Test
    @DisplayName("deletePolicy should fail when policy not found")
    void testDeletePolicyNotFound() {
        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(policyService.deletePolicy(POLICY_ID, TENANT_ID))
                .expectError(BillingPolicyNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("IDOR (Audit n°7 · #5): deletePolicy must not delete another tenant's policy")
    void testDeletePolicyRejectsCrossTenantAccess() {
        when(policyRepository.findByIdAndTenantId(POLICY_ID, OTHER_TENANT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(policyService.deletePolicy(POLICY_ID, OTHER_TENANT_ID))
                .expectError(BillingPolicyNotFoundException.class)
                .verify();

        verify(policyRepository, org.mockito.Mockito.never()).deleteById(any());
    }
}
