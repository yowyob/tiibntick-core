package com.yowyob.tiibntick.core.agency.billing.adapter.out.clients;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

public interface BillingPolicyCorePort {

    Mono<PolicyView> createPolicy(CreatePolicyRequest request);

    Mono<PolicyView> activatePolicy(UUID corePolicyId);

    Mono<PolicyView> archivePolicy(UUID corePolicyId);

    record CreatePolicyRequest(
            UUID tenantId,
            UUID agencyId,
            String name,
            String description,
            String currency,
            BigDecimal basePrice,
            BigDecimal pricePerKm,
            BigDecimal pricePerKg,
            BigDecimal minPrice
    ) {}

    record PolicyView(UUID id, String name, String status) {}
}
