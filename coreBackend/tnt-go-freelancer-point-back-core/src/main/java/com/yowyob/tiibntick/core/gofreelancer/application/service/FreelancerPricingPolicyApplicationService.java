package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.in.IBillingPolicyUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerPricingDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerPricingPolicyUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.FreelancerPricingRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerPricingPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * Freelancer rate grid on top of the billing layer.
 *
 * <p><strong>Source of truth.</strong> The grid is a {@link BillingPolicy} (template code
 * {@value FreelancerGridPolicyMapper#GRID_TEMPLATE_CODE}) owned by the delivery person and
 * ACTIVE; the pricing engine bills with it. The legacy {@code delivery_person_pricing} row is kept
 * as a mirror (written on every update) and as the fallback for grids saved before the billing
 * migration: reading such a grid transparently serves the legacy row, and the next update creates
 * the billing policy from it (lazy migration, no batch job).
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FreelancerPricingPolicyApplicationService implements FreelancerPricingPolicyUseCase {

    private final FreelancerPricingRepository freelancerPricingRepository;
    private final IBillingPolicyUseCase billingPolicyUseCase;
    private final FreelancerOwnershipGuard ownershipGuard;

    @Override
    public Mono<FreelancerPricingDTO> getPolicy(UUID freelancerId) {
        return findGridPolicy(freelancerId)
                .map(FreelancerGridPolicyMapper::toGrid)
                .switchIfEmpty(Mono.defer(() -> freelancerPricingRepository.findByDeliveryPersonId(freelancerId)
                        .map(FreelancerGridPolicyMapper::toGrid)));
    }

    @Override
    public Mono<FreelancerPricingDTO> upsertPolicy(UUID freelancerId, FreelancerPricingDTO request, UUID tenantId) {
        return findGridPolicy(freelancerId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(existing -> currentGrid(freelancerId, existing.orElse(null))
                        .map(current -> FreelancerGridPolicyMapper.merge(current, request))
                        .flatMap(merged -> saveLegacyMirror(freelancerId, merged)
                                .then(Mono.defer(() ->
                                        saveBilling(freelancerId, merged, existing.orElse(null), tenantId)))));
    }

    @Override
    public Mono<FreelancerPricingDTO> upsertPolicyAsCaller(UUID freelancerId, FreelancerPricingDTO request,
                                                           UUID tenantId, UUID callerId, boolean admin) {
        return ownershipGuard.check(freelancerId, callerId, admin)
                .then(Mono.defer(() -> upsertPolicy(freelancerId, request, tenantId)));
    }

    /** The freelancer's own grid policy (never a template-applied one), newest first. */
    private Mono<BillingPolicy> findGridPolicy(UUID freelancerId) {
        return billingPolicyUseCase.findByOwnerActorId(freelancerId.toString())
                .filter(p -> FreelancerGridPolicyMapper.GRID_TEMPLATE_CODE.equals(p.getTemplateCode()))
                .filter(p -> !PolicyStatus.ARCHIVED.equals(p.getStatus()))
                .sort(FreelancerGridPolicyMapper.BY_FRESHNESS.reversed())
                .next();
    }

    private Mono<FreelancerPricingDTO> currentGrid(UUID freelancerId, BillingPolicy policy) {
        if (policy != null) return Mono.just(FreelancerGridPolicyMapper.toGrid(policy));
        return freelancerPricingRepository.findByDeliveryPersonId(freelancerId)
                .map(FreelancerGridPolicyMapper::toGrid)
                .defaultIfEmpty(FreelancerPricingDTO.builder().currency(FreelancerGridPolicyMapper.DEFAULT_CURRENCY).build());
    }

    private Mono<FreelancerPricingPolicy> saveLegacyMirror(UUID freelancerId, FreelancerPricingDTO grid) {
        return freelancerPricingRepository.findByDeliveryPersonId(freelancerId)
                .defaultIfEmpty(FreelancerPricingPolicy.builder().deliveryPersonId(freelancerId).build())
                .flatMap(row -> {
                    row.setBaseFee(grid.getBaseFee());
                    row.setPricePerKm(grid.getPricePerKm());
                    row.setPricePerKg(grid.getPricePerKg());
                    row.setPricePerCbm(grid.getPricePerCbm());
                    row.setFragileSurcharge(grid.getFragileSurcharge());
                    row.setPerishableSurcharge(grid.getPerishableSurcharge());
                    row.setCurrency(grid.getCurrency());
                    return freelancerPricingRepository.save(row);
                });
    }

    private Mono<FreelancerPricingDTO> saveBilling(UUID freelancerId, FreelancerPricingDTO grid,
                                                   BillingPolicy existing, UUID tenantId) {
        if (existing != null) {
            FreelancerGridPolicyMapper.Rules rules = FreelancerGridPolicyMapper.toRules(grid);
            BillingPolicy changed = existing.toBuilder()
                    .pricingRules(rules.pricingRules()).surchargeRules(rules.surchargeRules()).build();
            return billingPolicyUseCase.updatePolicy(changed, existing.getTenantId())
                    .flatMap(saved -> ensureActive(saved));
        }
        if (tenantId == null) {
            log.warn("No tenant in the caller context: grid of delivery person {} stays in the legacy table "
                    + "until a tenant-scoped update creates its billing policy", freelancerId);
            return Mono.just(withSource(grid, "LEGACY"));
        }
        BillingPolicy fresh = FreelancerGridPolicyMapper.newPolicy(freelancerId.toString(), tenantId, grid);
        return billingPolicyUseCase.createPolicy(fresh).flatMap(this::ensureActive);
    }

    private Mono<FreelancerPricingDTO> ensureActive(BillingPolicy saved) {
        Mono<BillingPolicy> active = saved.isActive()
                ? Mono.just(saved)
                : billingPolicyUseCase.activatePolicy(saved.getId(), saved.getTenantId());
        return active.map(FreelancerGridPolicyMapper::toGrid);
    }

    private static FreelancerPricingDTO withSource(FreelancerPricingDTO grid, String source) {
        grid.setSource(source);
        return grid;
    }
}
