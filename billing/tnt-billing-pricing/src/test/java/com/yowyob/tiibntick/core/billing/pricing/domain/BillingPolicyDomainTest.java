package com.yowyob.tiibntick.core.billing.pricing.domain;

import com.yowyob.tiibntick.core.billing.pricing.domain.exception.InvalidPolicyException;
import com.yowyob.tiibntick.core.billing.pricing.domain.exception.PolicyNotActiveException;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PricingRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("BillingPolicy aggregate — state machine and invariants")
class BillingPolicyDomainTest {

    private BillingPolicy draft() {
        return BillingPolicy.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .name("Test Policy")
                .pricingRules(List.of(PricingRule.builder()
                        .id(UUID.randomUUID()).name("R")
                        .conditionExpression("weight <= 100")
                        .basePrice(Money.of(1000L, "XAF")).priority(10).build()))
                .surchargeRules(List.of())
                .promotions(List.of())
                .loyaltyRules(List.of())
                .commissionRules(List.of())
                .isDefault(false)
                .status(PolicyStatus.DRAFT)
                .validFrom(LocalDate.now())
                .build();
    }

    @Test
    @DisplayName("DRAFT → ACTIVE via activate()")
    void testActivate() {
        BillingPolicy active = draft().activate();
        assertThat(active.getStatus()).isEqualTo(PolicyStatus.ACTIVE);
        assertThat(active.isActive()).isTrue();
    }

    @Test
    @DisplayName("ACTIVE → INACTIVE via deactivate()")
    void testDeactivate() {
        BillingPolicy inactive = draft().activate().deactivate();
        assertThat(inactive.getStatus()).isEqualTo(PolicyStatus.INACTIVE);
        assertThat(inactive.isActive()).isFalse();
    }

    @Test
    @DisplayName("deactivate on DRAFT policy throws PolicyNotActiveException")
    void testDeactivateDraft() {
        assertThatThrownBy(() -> draft().deactivate())
                .isInstanceOf(PolicyNotActiveException.class);
    }

    @Test
    @DisplayName("ARCHIVED policy cannot be activated")
    void testActivateArchived() {
        BillingPolicy archived = draft().archive();
        assertThatThrownBy(archived::activate)
                .isInstanceOf(InvalidPolicyException.class);
    }

    @Test
    @DisplayName("addRule increases pricingRules count by 1")
    void testAddRule() {
        BillingPolicy policy = draft();
        int before = policy.getPricingRules().size();
        PricingRule newRule = PricingRule.builder()
                .id(UUID.randomUUID()).name("New Rule")
                .conditionExpression("distance > 10")
                .basePrice(Money.of(2000L, "XAF")).priority(20).build();
        BillingPolicy updated = policy.addRule(newRule);
        assertThat(updated.getPricingRules()).hasSize(before + 1);
    }

    @Test
    @DisplayName("removeRule decreases pricingRules count by 1")
    void testRemoveRule() {
        BillingPolicy policy = draft();
        UUID ruleId = policy.getPricingRules().get(0).getId();
        BillingPolicy updated = policy.removeRule(ruleId);
        assertThat(updated.getPricingRules()).isEmpty();
    }

    @Test
    @DisplayName("sortedPricingRules returns rules ordered by ascending priority")
    void testSortedRules() {
        PricingRule r100 = PricingRule.builder().id(UUID.randomUUID()).name("Low")
                .conditionExpression("weight <= 10").basePrice(Money.of(500L, "XAF")).priority(100).build();
        PricingRule r10 = PricingRule.builder().id(UUID.randomUUID()).name("High")
                .conditionExpression("weight <= 100").basePrice(Money.of(1000L, "XAF")).priority(10).build();

        BillingPolicy policy = draft().withPricingRules(List.of(r100, r10));
        List<PricingRule> sorted = policy.sortedPricingRules();

        assertThat(sorted.get(0).getPriority()).isEqualTo(10);
        assertThat(sorted.get(1).getPriority()).isEqualTo(100);
    }

    @Test
    @DisplayName("isValidNow is true when today is within validFrom..validTo")
    void testIsValidNow() {
        BillingPolicy policy = draft().toBuilder()
                .validFrom(LocalDate.now().minusDays(5))
                .validTo(LocalDate.now().plusDays(5))
                .build();
        assertThat(policy.isValidNow()).isTrue();
    }

    @Test
    @DisplayName("isValidNow is false when validTo is in the past")
    void testIsValidNowExpired() {
        BillingPolicy policy = draft().toBuilder()
                .validFrom(LocalDate.now().minusDays(10))
                .validTo(LocalDate.now().minusDays(1))
                .build();
        assertThat(policy.isValidNow()).isFalse();
    }
}
