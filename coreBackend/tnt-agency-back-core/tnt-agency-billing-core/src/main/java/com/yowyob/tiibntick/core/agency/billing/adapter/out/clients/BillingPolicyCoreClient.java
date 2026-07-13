package com.yowyob.tiibntick.core.agency.billing.adapter.out.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class BillingPolicyCoreClient implements BillingPolicyCorePort {

    private static final Logger log = LoggerFactory.getLogger(BillingPolicyCoreClient.class);
    private static final String BASE = "/api/v1/billing/policies";

    private final WebClient webClient;

    public BillingPolicyCoreClient(@Qualifier("agencyPlatformWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<PolicyView> createPolicy(CreatePolicyRequest request) {
        Map<String, Object> body = Map.of(
                "name", request.name(),
                "description", request.description() != null ? request.description() : "",
                "tenantId", request.tenantId().toString(),
                "agencyId", request.agencyId().toString(),
                "ownerType", "AGENCY",
                "ownerActorId", request.agencyId().toString(),
                "isDefault", false,
                "pricingRules", List.of(Map.of(
                        "name", request.name(),
                        "conditionExpression", "true",
                        "basePriceAmount", request.basePrice(),
                        "currencyCode", request.currency(),
                        "perKmRateAmount", request.pricePerKm(),
                        "perKgRateAmount", request.pricePerKg(),
                        "minimumPriceAmount", request.minPrice(),
                        "priority", 1
                ))
        );
        return webClient.post()
                .uri(BASE)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(CorePolicyResponse.class)
                .map(r -> new PolicyView(r.id(), r.name(), r.status()))
                .doOnError(e -> log.warn("[BillingPolicyCore] create failed agency={}: {}",
                        request.agencyId(), e.getMessage()));
    }

    @Override
    public Mono<PolicyView> activatePolicy(UUID corePolicyId) {
        return patchStatus(corePolicyId, "/activate");
    }

    @Override
    public Mono<PolicyView> archivePolicy(UUID corePolicyId) {
        return patchStatus(corePolicyId, "/archive");
    }

    private Mono<PolicyView> patchStatus(UUID corePolicyId, String suffix) {
        return webClient.patch()
                .uri(BASE + "/{policyId}" + suffix, corePolicyId)
                .retrieve()
                .bodyToMono(CorePolicyResponse.class)
                .map(r -> new PolicyView(r.id(), r.name(), r.status()))
                .doOnError(e -> log.warn("[BillingPolicyCore] {} failed policyId={}: {}",
                        suffix, corePolicyId, e.getMessage()));
    }

    private record CorePolicyResponse(UUID id, String name, String status) {}
}
