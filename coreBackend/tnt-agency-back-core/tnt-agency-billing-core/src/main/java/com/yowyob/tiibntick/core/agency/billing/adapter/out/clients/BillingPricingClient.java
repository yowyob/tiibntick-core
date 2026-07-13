package com.yowyob.tiibntick.core.agency.billing.adapter.out.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class BillingPricingClient implements BillingPricingPort {

    private static final Logger log = LoggerFactory.getLogger(BillingPricingClient.class);
    private static final String EVALUATE_PATH = "/api/v1/billing/pricing/evaluate";

    private final WebClient webClient;

    public BillingPricingClient(@Qualifier("agencyPlatformWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<PricingResult> evaluate(PricingRequest request) {
        return webClient.post()
                .uri(EVALUATE_PATH)
                .header("X-Tenant-Id", request.tenantId().toString())
                .bodyValue(Map.of(
                        "policyId", request.policyId().toString(),
                        "tenantId", request.tenantId().toString(),
                        "agencyId", request.agencyId().toString(),
                        "missionId", request.missionId() != null ? request.missionId().toString() : "",
                        "weightKg", request.weightKg(),
                        "distanceKm", request.distanceKm()
                ))
                .retrieve()
                .bodyToMono(PriceEvaluationResponse.class)
                .map(r -> new PricingResult(
                        r.sellingPrice().amount(),
                        r.sellingPrice().currency()))
                .onErrorResume(e -> {
                    log.warn("[BillingPricing] evaluate failed policyId={}: {}", request.policyId(), e.getMessage());
                    return Mono.empty();
                });
    }

    private record PriceEvaluationResponse(MoneyDto sellingPrice) {}

    private record MoneyDto(BigDecimal amount, String currency) {}
}
