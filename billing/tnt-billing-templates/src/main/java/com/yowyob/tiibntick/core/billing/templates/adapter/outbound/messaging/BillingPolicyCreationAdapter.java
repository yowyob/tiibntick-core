package com.yowyob.tiibntick.core.billing.templates.adapter.outbound.messaging;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAccessLevel;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.in.IBillingPolicyUseCase;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplatePolicyMaterializer;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.IBillingPolicyCreationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates the {@link BillingPolicy} for an applied template through {@code tnt-billing-pricing}.
 *
 * <p>Until now this adapter was a stub returning a random UUID, so "applying" a template stored
 * nothing and no price ever followed from it. The rules are built by
 * {@link TemplatePolicyMaterializer} (real engine rules, not the display-only pseudo-DSL) and the
 * policy is created in {@code DRAFT}: the actor activates it explicitly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingPolicyCreationAdapter implements IBillingPolicyCreationPort {

    private final IBillingPolicyUseCase policyUseCase;
    private final TemplatePolicyMaterializer materializer;

    @Override
    public Mono<UUID> createBillingPolicy(BillingPolicyCreationRequest request) {
        return Mono.fromCallable(() -> buildPolicy(request))
                .flatMap(policyUseCase::createPolicy)
                .map(BillingPolicy::getId)
                .doOnSuccess(id -> log.info("BillingPolicy {} created for actor={} from template={}",
                        id, request.ownerActorId(), request.templateCode()));
    }

    /** Previews are computed by the pricing engine itself (see the preview use case). */
    @Override
    public Mono<Map<String, Object>> computePreview(String templateCode, Map<String, String> customizedParameters,
                                                    Map<String, Object> scenarioContext) {
        return Mono.just(Map.of());
    }

    /** The policy the template yields, not yet persisted — also used to preview the exact price. */
    public BillingPolicy buildPolicy(BillingPolicyCreationRequest request) {
        TemplatePolicyMaterializer.Materialized m =
                materializer.materialize(request.templateCode(), request.appliedParameters());
        var ownerType = com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyOwnerType
                .valueOf(request.ownerType().name());
        return BillingPolicy.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.fromString(request.tenantId()))
                .name(request.policyName())
                .description("Created from template " + request.templateCode())
                .ownerType(ownerType)
                .ownerActorId(request.ownerActorId())
                .isFromTemplate(true)
                .templateCode(request.templateCode())
                .dslAccessLevel(switch (ownerType) {
                    case AGENCY, ADMIN, MARKET -> DslAccessLevel.FULL;
                    case FREELANCER_ORG, POINT, LINK -> DslAccessLevel.SIMPLIFIED;
                })
                .pricingRules(m.pricingRules())
                .surchargeRules(m.surchargeRules())
                .promotions(List.of())
                .loyaltyRules(List.of())
                .commissionRules(List.of())
                .specialSurcharges(List.of())
                .hubStorageRules(List.of())
                .networkTransitRules(List.of())
                .status(PolicyStatus.DRAFT)
                .validFrom(LocalDate.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
