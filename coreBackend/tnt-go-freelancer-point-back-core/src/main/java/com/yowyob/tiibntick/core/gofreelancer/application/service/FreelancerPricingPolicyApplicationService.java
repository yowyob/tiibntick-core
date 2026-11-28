package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerVehicleRepository;
import com.yowyob.tiibntick.core.gofreelancer.config.GofpCourierPricingProperties;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerVehicle;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.in.IBillingPolicyUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerPricingDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerPricingPolicyUseCase;
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
 * ACTIVE; the pricing engine bills with it. The legacy {@code delivery_person_pricing} table is no
 * longer read nor written: its grids were copied into billing policies once, in the system tenant
 * (legacy pricing backfill, lot L4; table dropped by gdd-final). A grid created without a tenant in the caller's
 * context goes to the system tenant too (the module's rule, see {@link TenantContextHolder}).
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FreelancerPricingPolicyApplicationService implements FreelancerPricingPolicyUseCase {

    private final IBillingPolicyUseCase billingPolicyUseCase;
    private final FreelancerOwnershipGuard ownershipGuard;

    /** Platform grids, the starting point of a courier's first grid (D-058). Optional for slim test contexts. */
    private GofpCourierPricingProperties courierPricing = new GofpCourierPricingProperties();
    private FreelancerVehicleRepository vehicleRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setCourierPricing(GofpCourierPricingProperties courierPricing) {
        if (courierPricing != null) this.courierPricing = courierPricing;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setVehicleRepository(FreelancerVehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    public Mono<FreelancerPricingDTO> getPolicy(UUID freelancerId) {
        return findGridPolicy(freelancerId)
                .map(FreelancerGridPolicyMapper::toGrid);
    }

    @Override
    public Mono<FreelancerPricingDTO> upsertPolicy(UUID freelancerId, FreelancerPricingDTO request, UUID tenantId) {
        return findGridPolicy(freelancerId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(existing -> currentGrid(freelancerId, existing.orElse(null))
                        .flatMap(current -> saveBilling(freelancerId,
                                FreelancerGridPolicyMapper.merge(current, request),
                                existing.orElse(null), tenantId)));
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

    /**
     * The grid a change applies to: the courier's own grid, or, for his first grid, the platform grid of his vehicle
     * (D-058: the platform price is a proposal the courier adjusts field by field; a field he does not send keeps the
     * proposed value instead of falling to 0).
     */
    private Mono<FreelancerPricingDTO> currentGrid(UUID freelancerId, BillingPolicy policy) {
        if (policy != null) return Mono.just(FreelancerGridPolicyMapper.toGrid(policy));
        Mono<String> vehicleType = vehicleRepository == null ? Mono.empty()
                : vehicleRepository.findByFreelancerId(freelancerId)
                        .mapNotNull(FreelancerVehicle::getVehicleType)
                        .onErrorResume(e -> {
                            log.warn("Vehicle of delivery person {} unreadable ({}): first grid starts from the moto "
                                    + "platform grid", freelancerId, e.getMessage());
                            return Mono.empty();
                        });
        return vehicleType.map(courierPricing::gridForVehicle)
                .switchIfEmpty(Mono.fromSupplier(() -> courierPricing.gridForVehicle(null)));
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
        UUID tenant = tenantId;
        if (tenant == null) {
            log.info("No tenant in the caller context: grid of delivery person {} created in the system tenant",
                    freelancerId);
            tenant = TenantContextHolder.SYSTEM_TENANT;
        }
        BillingPolicy fresh = FreelancerGridPolicyMapper.newPolicy(freelancerId.toString(), tenant, grid);
        return billingPolicyUseCase.createPolicy(fresh).flatMap(this::ensureActive);
    }

    private Mono<FreelancerPricingDTO> ensureActive(BillingPolicy saved) {
        Mono<BillingPolicy> active = saved.isActive()
                ? Mono.just(saved)
                : billingPolicyUseCase.activatePolicy(saved.getId(), saved.getTenantId());
        return active.map(FreelancerGridPolicyMapper::toGrid);
    }
}
