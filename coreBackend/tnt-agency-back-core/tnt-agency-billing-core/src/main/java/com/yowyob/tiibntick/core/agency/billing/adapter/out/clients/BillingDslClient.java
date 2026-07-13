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
public class BillingDslClient implements BillingEvaluatorPort {

    private static final Logger log = LoggerFactory.getLogger(BillingDslClient.class);
    private static final String PATH = "/api/v1/billing/dsl/evaluate";

    private final WebClient webClient;

    public BillingDslClient(@Qualifier("agencyPlatformWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<BillingResult> evaluate(BillingEvaluationRequest request) {
        Map<String, Object> body = Map.of(
                "policyId", request.policyId(),
                "tenantId", request.tenantId(),
                "agencyId", request.agencyId(),
                "distanceKm", request.distanceKm(),
                "weightKg", request.weightKg(),
                "volumeCbm", request.volumeCbm()
        );
        return webClient.post()
                .uri(PATH)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(CoreEvaluationResult.class)
                .map(r -> new BillingResult(
                        r.finalPrice().amount(),
                        r.basePrice().amount(),
                        r.perKmTotal().amount(),
                        r.perKgTotal().amount(),
                        sumDiscounts(r),
                        r.finalPrice().currency()
                ))
                .onErrorResume(e -> {
                    log.warn("[BillingDSL] evaluate failed policyId={}: {}", request.policyId(), e.getMessage());
                    return Mono.just(BillingResult.zero("XAF"));
                });
    }

    private BigDecimal sumDiscounts(CoreEvaluationResult result) {
        if (result.discounts() == null) {
            return BigDecimal.ZERO;
        }
        return result.discounts().stream()
                .map(MoneyDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private record CoreEvaluationResult(
            MoneyDto basePrice,
            MoneyDto perKmTotal,
            MoneyDto perKgTotal,
            java.util.List<MoneyDto> surcharges,
            java.util.List<MoneyDto> discounts,
            MoneyDto finalPrice,
            int matchedRuleCount
    ) {}

    private record MoneyDto(BigDecimal amount, String currency) {}
}
