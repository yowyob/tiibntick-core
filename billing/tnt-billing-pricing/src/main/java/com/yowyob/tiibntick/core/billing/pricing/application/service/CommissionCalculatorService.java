package com.yowyob.tiibntick.core.billing.pricing.application.service;

import com.yowyob.tiibntick.core.billing.pricing.domain.exception.BillingPolicyNotFoundException;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.CommissionRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.CommissionAppliesTo;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.IBillingPolicyRepository;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Computes commission breakdowns for billing policies.
 *
 * <h3> additions — FreelancerOrg multi-actor split</h3>
 * <ul>
 *   <li>{@link #computeFreelancerOrgSplit} — splits the total commission between the
 *       FreelancerOrg OWNER and a SUB_DELIVERER based on their respective commission rules.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionCalculatorService {

    private final IBillingPolicyRepository policyRepository;

    /**
     * Computes the standard commission breakdown for a single actor type.
     *
     * @param policyId       the billing policy
     * @param sellingPrice   the final selling price before commissions
     * @param delivererType  the type of deliverer actor
     * @return the commission breakdown
     */
    public Mono<CommissionBreakdown> compute(UUID policyId, Money sellingPrice,
                                              CommissionAppliesTo delivererType) {
        return policyRepository.findById(policyId)
                .switchIfEmpty(Mono.error(new BillingPolicyNotFoundException(policyId)))
                .map(policy -> {
                    Money delivererCommission = Money.zeroXAF();
                    Money platformFee = Money.zeroXAF();
                    Money agencyCommission = Money.zeroXAF();

                    if (policy.getCommissionRules() != null) {
                        for (CommissionRule rule : policy.getCommissionRules()) {
                            if (rule.appliesTo(delivererType)) {
                                delivererCommission = delivererCommission.add(
                                        rule.computeDelivererCommission(sellingPrice));
                                platformFee = platformFee.add(
                                        rule.computePlatformFee(sellingPrice));
                                agencyCommission = agencyCommission.add(
                                        rule.computeAgencyCommission(sellingPrice));
                            }
                        }
                    }

                    if (policy.getPlatformFeeRule() != null) {
                        platformFee = policy.getPlatformFeeRule().compute(sellingPrice);
                    }

                    return new CommissionBreakdown(sellingPrice, delivererCommission,
                            platformFee, agencyCommission);
                });
    }

    /**
     *  — Computes the FreelancerOrg multi-actor commission split.
     *
     * <p>When a FreelancerOrg OWNER delegates a mission to a SUB_DELIVERER:
     * <ol>
     *   <li>The platform takes its fee from the total selling price.</li>
     *   <li>The remainder is split between the OWNER (org revenue) and the SUB_DELIVERER
     *       according to their respective commission rules.</li>
     * </ol>
     *
     * @param policyId       the FreelancerOrg billing policy
     * @param sellingPrice   the final selling price (after all surcharges, discounts, taxes)
     * @param subDelivererPct percentage to allocate to the sub-deliverer (0–100)
     * @return the multi-actor commission split
     */
    public Mono<FreelancerOrgCommissionSplit> computeFreelancerOrgSplit(
            UUID policyId, Money sellingPrice, BigDecimal subDelivererPct) {
        return policyRepository.findById(policyId)
                .switchIfEmpty(Mono.error(new BillingPolicyNotFoundException(policyId)))
                .map(policy -> {
                    Money platformFee = Money.zeroXAF();
                    if (policy.getPlatformFeeRule() != null) {
                        platformFee = policy.getPlatformFeeRule().compute(sellingPrice);
                    }

                    // Revenue available for the org after platform fee
                    Money orgRevenue = sellingPrice.subtract(platformFee);

                    // Sub-deliverer allocation
                    BigDecimal pct = subDelivererPct != null ? subDelivererPct : BigDecimal.ZERO;
                    Money subDelivererShare = orgRevenue.percentage(pct);

                    // OWNER keeps the remainder
                    Money ownerShare = orgRevenue.subtract(subDelivererShare);

                    log.debug("FreelancerOrg split: total={} platform={} owner={} sub={}",
                            sellingPrice, platformFee, ownerShare, subDelivererShare);

                    return new FreelancerOrgCommissionSplit(
                            sellingPrice, platformFee, ownerShare, subDelivererShare, pct);
                });
    }

    // ── Result records ────────────────────────────────────────────────────────

    public record CommissionBreakdown(
            Money sellingPrice,
            Money delivererCommission,
            Money platformFee,
            Money agencyCommission
    ) {
        public Money netToAgency() {
            return sellingPrice.subtract(delivererCommission).subtract(platformFee);
        }
    }

    /**
     *  — Commission split for a FreelancerOrg multi-actor scenario.
     *
     * @param sellingPrice       total selling price charged to the client
     * @param platformFee        TiiBnTick platform fee
     * @param ownerShare         amount allocated to the FreelancerOrg OWNER
     * @param subDelivererShare  amount allocated to the SUB_DELIVERER
     * @param subDelivererPct    percentage used for the sub-deliverer allocation
     */
    public record FreelancerOrgCommissionSplit(
            Money sellingPrice,
            Money platformFee,
            Money ownerShare,
            Money subDelivererShare,
            BigDecimal subDelivererPct
    ) {
        /** Total org revenue = ownerShare + subDelivererShare. */
        public Money totalOrgRevenue() {
            return ownerShare.add(subDelivererShare);
        }
    }
}
