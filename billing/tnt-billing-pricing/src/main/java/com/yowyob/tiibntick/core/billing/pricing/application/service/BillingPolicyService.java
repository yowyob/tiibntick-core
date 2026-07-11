package com.yowyob.tiibntick.core.billing.pricing.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.billing.pricing.domain.exception.BillingPolicyNotFoundException;
import com.yowyob.tiibntick.core.billing.pricing.domain.exception.InvalidPolicyException;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.FleetCostParameters;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.in.IBillingPolicyUseCase;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.BillingPolicyAnchorPayload;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.BillingPolicyAnchorPort;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.IBillingPolicyRepository;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAccessLevel;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application service implementing {@link IBillingPolicyUseCase}.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@link #createPolicyFromTemplate} — template-based policy creation</li>
 *   <li>{@link #assignPolicyToOrg} — assigns a policy to a FreelancerOrg actor</li>
 *   <li>{@link #findByOwnerActorId} — owner-scoped query</li>
 *   <li>{@link #computeOperationalCost} — fleet cost estimation</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingPolicyService implements IBillingPolicyUseCase {

    private final IBillingPolicyRepository policyRepository;
    private final BillingPolicyAnchorPort billingPolicyAnchorPort;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<BillingPolicy> createPolicy(BillingPolicy policy) {
        if (policy.getPricingRules() == null || policy.getPricingRules().isEmpty()) {
            return Mono.error(new InvalidPolicyException(
                    "A BillingPolicy must have at least one PricingRule"));
        }
        BillingPolicy toCreate = policy.toBuilder()
                .id(policy.getId() != null ? policy.getId() : UUID.randomUUID())
                .status(PolicyStatus.DRAFT)
                .validFrom(policy.getValidFrom() != null ? policy.getValidFrom() : LocalDate.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return policyRepository.save(toCreate);
    }

    @Override
    public Mono<BillingPolicy> updatePolicy(BillingPolicy policy) {
        return policyRepository.findById(policy.getId())
                .switchIfEmpty(Mono.error(new BillingPolicyNotFoundException(policy.getId())))
                .flatMap(existing -> {
                    BillingPolicy updated = existing.toBuilder()
                            .pricingRules(policy.getPricingRules())
                            .surchargeRules(policy.getSurchargeRules())
                            .promotions(policy.getPromotions())
                            .loyaltyRules(policy.getLoyaltyRules())
                            .commissionRules(policy.getCommissionRules())
                            .platformFeeRule(policy.getPlatformFeeRule())
                            //  — preserve new fields if provided
                            .specialSurcharges(policy.getSpecialSurcharges() != null
                                    ? policy.getSpecialSurcharges() : existing.getSpecialSurcharges())
                            .hubStorageRules(policy.getHubStorageRules() != null
                                    ? policy.getHubStorageRules() : existing.getHubStorageRules())
                            .networkTransitRules(policy.getNetworkTransitRules() != null
                                    ? policy.getNetworkTransitRules() : existing.getNetworkTransitRules())
                            .fleetCostParameters(policy.getFleetCostParameters() != null
                                    ? policy.getFleetCostParameters() : existing.getFleetCostParameters())
                            .updatedAt(Instant.now())
                            .build();
                    return policyRepository.save(updated);
                });
    }

    @Override
    public Mono<BillingPolicy> activatePolicy(UUID policyId) {
        return policyRepository.findById(policyId)
                .switchIfEmpty(Mono.error(new BillingPolicyNotFoundException(policyId)))
                .flatMap(policy -> policyRepository.save(policy.activate()))
                .flatMap(activated -> anchorActivation(activated).thenReturn(activated));
    }

    /**
     * Anchors a policy activation on the blockchain via {@code tnt-trust-core}, best-effort.
     * A trust-anchoring failure must never fail policy activation.
     */
    private Mono<Void> anchorActivation(BillingPolicy policy) {
        final String summaryJson = buildPolicySummaryJson(policy);
        final BillingPolicyAnchorPayload payload = new BillingPolicyAnchorPayload(
                policy.getTenantId(), policy.getId(),
                policy.getOwnerActorId() != null
                        ? policy.getOwnerActorId()
                        : policy.getAgencyId() != null ? policy.getAgencyId().toString() : null,
                summaryJson, Instant.now());
        return billingPolicyAnchorPort.anchor(payload)
                .onErrorResume(e -> {
                    log.warn("Failed to anchor billing policy activation on-chain — policyId={}: {}",
                            policy.getId(), e.getMessage());
                    return Mono.empty();
                });
    }

    /** Builds a compact JSON summary of the activated policy's metadata for on-chain anchoring. */
    private String buildPolicySummaryJson(BillingPolicy policy) {
        final Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", policy.getName());
        summary.put("ownerType", policy.getOwnerType());
        summary.put("status", policy.getStatus());
        summary.put("validFrom", policy.getValidFrom());
        summary.put("pricingRuleCount", policy.getPricingRules() != null ? policy.getPricingRules().size() : 0);
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            log.warn("Failed to serialize policy summary for policyId={}: {}", policy.getId(), e.getMessage());
            return "{}";
        }
    }

    @Override
    public Mono<BillingPolicy> deactivatePolicy(UUID policyId) {
        return policyRepository.findById(policyId)
                .switchIfEmpty(Mono.error(new BillingPolicyNotFoundException(policyId)))
                .flatMap(policy -> policyRepository.save(policy.deactivate()));
    }

    @Override
    public Mono<BillingPolicy> archivePolicy(UUID policyId) {
        return policyRepository.findById(policyId)
                .switchIfEmpty(Mono.error(new BillingPolicyNotFoundException(policyId)))
                .flatMap(policy -> policyRepository.save(policy.archive()));
    }

    @Override
    public Mono<BillingPolicy> findById(UUID policyId) {
        return policyRepository.findById(policyId)
                .switchIfEmpty(Mono.error(new BillingPolicyNotFoundException(policyId)));
    }

    @Override
    public Mono<BillingPolicy> findDefaultForTenant(UUID tenantId) {
        return policyRepository.findDefaultByTenantId(tenantId)
                .switchIfEmpty(Mono.error(new BillingPolicyNotFoundException(
                        "No default policy for tenant: " + tenantId)));
    }

    @Override
    public Flux<BillingPolicy> findByTenantId(UUID tenantId) {
        return policyRepository.findByTenantId(tenantId);
    }

    @Override
    public Flux<BillingPolicy> findActiveByTenantId(UUID tenantId) {
        return policyRepository.findActiveByTenantId(tenantId);
    }

    @Override
    public Mono<Void> deletePolicy(UUID policyId) {
        return policyRepository.existsById(policyId)
                .flatMap(exists -> {
                    if (!exists) return Mono.error(new BillingPolicyNotFoundException(policyId));
                    return policyRepository.deleteById(policyId);
                });
    }

    // Multi-owner support ─────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Creates a minimal policy with the appropriate DSL access level for the owner type.
     * Currently creates a blank policy with the specified owner metadata; actual template
     * rules will be injected by the template engine (tnt-settings-core integration in ).
     */
    @Override
    public Mono<BillingPolicy> createPolicyFromTemplate(String templateCode,
                                                          PolicyOwnerType ownerType,
                                                          String ownerActorId,
                                                          UUID tenantId,
                                                          Map<String, Object> params) {
        DslAccessLevel accessLevel = resolveDslAccessLevel(ownerType);
        //String currency = params != null && params.containsKey("currency")
        //        ? (String) params.get("currency") : "XAF";

        // Build a minimal policy seeded from the template code.
        // Full template rule injection is deferred to tnt-settings-core ().
        BillingPolicy draft = BillingPolicy.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name(templateCode + " — " + ownerActorId)
                .description("Auto-created from template: " + templateCode)
                .ownerType(ownerType)
                .ownerActorId(ownerActorId)
                .isFromTemplate(true)
                .templateCode(templateCode)
                .dslAccessLevel(accessLevel)
                .pricingRules(List.of())
                .surchargeRules(List.of())
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

        return policyRepository.save(draft)
                .doOnSuccess(p -> log.info(
                        "Created policy {} from template '{}' for owner {} (type: {})",
                        p.getId(), templateCode, ownerActorId, ownerType));
    }

    @Override
    public Mono<BillingPolicy> assignPolicyToOrg(UUID policyId, String orgId,
                                                   PolicyOwnerType ownerType) {
        return policyRepository.findById(policyId)
                .switchIfEmpty(Mono.error(new BillingPolicyNotFoundException(policyId)))
                .flatMap(policy -> {
                    BillingPolicy updated = policy.toBuilder()
                            .ownerType(ownerType)
                            .ownerActorId(orgId)
                            .dslAccessLevel(resolveDslAccessLevel(ownerType))
                            .updatedAt(Instant.now())
                            .build();
                    return policyRepository.save(updated);
                })
                .doOnSuccess(p -> log.info(
                        "Policy {} assigned to org {} (type: {})", policyId, orgId, ownerType));
    }

    @Override
    public Flux<BillingPolicy> findByOwnerActorId(String ownerActorId) {
        return policyRepository.findByOwnerActorId(ownerActorId);
    }

    @Override
    public Mono<BigDecimal> computeOperationalCost(UUID policyId, PricingContext ctx,
                                                     FleetCostParameters fleetParams) {
        return policyRepository.findById(policyId)
                .switchIfEmpty(Mono.error(new BillingPolicyNotFoundException(policyId)))
                .map(policy -> {
                    FleetCostParameters params = fleetParams != null
                            ? fleetParams : policy.getFleetCostParameters();
                    if (params == null) {
                        log.warn("No FleetCostParameters available for policy {}", policyId);
                        return BigDecimal.ZERO;
                    }
                    // Estimate duration as 1.5 min per km (average urban speed ~40 km/h)
                    double estimatedDurationMin = ctx.getDistanceKm() * 1.5;
                    double terrainFactor = ctx.isRoadDegraded() ? 1.5 : 1.0;
                    boolean isRaining = ctx.isRaining();
                    return params.estimateCost(
                            ctx.getDistanceKm(),
                            estimatedDurationMin,
                            terrainFactor,
                            isRaining,
                            policy.getPricingRules() != null && !policy.getPricingRules().isEmpty()
                                    ? policy.getPricingRules().get(0).getBasePrice().getAmount()
                                    : BigDecimal.ZERO);
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves the DSL access level for a given owner type.
     *
     * <ul>
     *   <li>AGENCY, ADMIN, MARKET → FULL</li>
     *   <li>FREELANCER_ORG, POINT, LINK → SIMPLIFIED</li>
     * </ul>
     */
    private DslAccessLevel resolveDslAccessLevel(PolicyOwnerType ownerType) {
        if (ownerType == null) return DslAccessLevel.FULL;
        return switch (ownerType) {
            case AGENCY, ADMIN, MARKET -> DslAccessLevel.FULL;
            case FREELANCER_ORG, POINT, LINK -> DslAccessLevel.SIMPLIFIED;
        };
    }
}
