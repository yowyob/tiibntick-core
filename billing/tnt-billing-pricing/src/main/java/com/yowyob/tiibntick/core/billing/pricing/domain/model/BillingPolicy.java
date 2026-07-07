package com.yowyob.tiibntick.core.billing.pricing.domain.model;

import com.yowyob.tiibntick.core.billing.pricing.domain.exception.InvalidPolicyException;
import com.yowyob.tiibntick.core.billing.pricing.domain.exception.PolicyNotActiveException;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAccessLevel;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PackageType;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Billing policy aggregate root.
 *
 * <p>A policy defines the complete pricing configuration for a given owner
 * (agency, FreelancerOrg, Hub point, Link network, etc.).
 *
 * <h3> additions — FreelancerOrg and multi-owner support</h3>
 * <ul>
 *   <li>{@link #ownerType} — classifies the policy owner (AGENCY, FREELANCER_ORG …)</li>
 *   <li>{@link #ownerActorId} — UUID string of the policy owner actor (logical ref)</li>
 *   <li>{@link #isFromTemplate} — whether this policy was created from a template</li>
 *   <li>{@link #templateCode} — the code of the source template (if any)</li>
 *   <li>{@link #dslAccessLevel} — controls DSL authoring permissions for this owner</li>
 *   <li>{@link #specialSurcharges} — list of conditional special surcharge rules</li>
 *   <li>{@link #hubStorageRules} — storage fee rules for Hub Point owners</li>
 *   <li>{@link #networkTransitRules} — hop-based transit fee rules for Link owners</li>
 *   <li>{@link #fleetCostParameters} — operational cost params for FreelancerOrg owners</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder(toBuilder = true)
public class BillingPolicy {

    private final UUID id;
    private final UUID tenantId;
    private final UUID agencyId;
    private final String name;
    private final String description;

    @With private final List<PricingRule> pricingRules;
    @With private final List<SurchargeRule> surchargeRules;
    @With private final List<Promotion> promotions;
    @With private final List<LoyaltyRule> loyaltyRules;
    @With private final List<CommissionRule> commissionRules;
    @With private final PlatformFeeRule platformFeeRule;

    private final boolean isDefault;
    @With private final PolicyStatus status;

    private final LocalDate validFrom;
    private final LocalDate validTo;
    private final Instant createdAt;
    @With private final Instant updatedAt;

    // Multi-owner support ────────────────────────────────────────────

    /**
     * Classification of this policy's owner.
     * Determines the template catalog and DSL access level.
     * Null for legacy policies (backward-compatible — treated as AGENCY).
     */
    @With private final PolicyOwnerType ownerType;

    /**
     * UUID string of the policy owner actor.
     * For FREELANCER_ORG: the FreelancerOrganization id.
     * For POINT: the HubPoint id.
     * For AGENCY: same as agencyId (backward-compatible).
     * Logical reference — no physical FK across modules.
     */
    @With private final String ownerActorId;

    /**
     * Whether this policy was instantiated from a policy template.
     * Template-based policies inherit base rules but allow overrides within access level.
     */
    private final boolean isFromTemplate;

    /**
     * Code of the policy template used to create this policy.
     * Null when created from scratch.
     * Examples: "FREELANCER_STANDARD", "HUB_STORAGE_BASIC", "AGENCY_FULL".
     */
    private final String templateCode;

    /**
     * DSL authoring access level for the owner of this policy.
     * Controls which DSL variables and constructs the owner may use.
     * Defaults to {@link DslAccessLevel#FULL} for AGENCY and ADMIN owners.
     */
    @With private final DslAccessLevel dslAccessLevel;

    // Advanced rule types ─────────────────────────────────────────────

    /**
     * Special conditional surcharges evaluated by DSL expressions.
     * Supports stacking modes (CUMULATIVE, EXCLUSIVE_HIGHEST, CAPPED).
     * Used for refrigeration, night delivery, weekend premiums, etc.
     */
    @With private final List<SpecialSurchargeRule> specialSurcharges;

    /**
     * Hub storage fee rules for HUB_POINT owners.
     * Applied when a parcel remains at the hub beyond the free storage period.
     */
    @With private final List<HubStorageRule> hubStorageRules;

    /**
     * Network transit fee rules for LINK owners.
     * Applied per relay hop in the network route.
     */
    @With private final List<NetworkTransitRule> networkTransitRules;

    /**
     * Fleet operational cost parameters for FREELANCER_ORG owners.
     * Used to compute the true operational cost of a delivery for margin analysis.
     */
    @With private final FleetCostParameters fleetCostParameters;

    // ── Lifecycle mutations ───────────────────────────────────────────────────

    public BillingPolicy activate() {
        if (PolicyStatus.ARCHIVED.equals(status)) {
            throw new InvalidPolicyException("Cannot activate an archived policy: " + id);
        }
        return this.withStatus(PolicyStatus.ACTIVE).withUpdatedAt(Instant.now());
    }

    public BillingPolicy deactivate() {
        if (!PolicyStatus.ACTIVE.equals(status)) {
            throw new PolicyNotActiveException(id);
        }
        return this.withStatus(PolicyStatus.INACTIVE).withUpdatedAt(Instant.now());
    }

    public BillingPolicy archive() {
        return this.withStatus(PolicyStatus.ARCHIVED).withUpdatedAt(Instant.now());
    }

    public BillingPolicy addRule(PricingRule rule) {
        List<PricingRule> updated = new ArrayList<>(pricingRules != null ? pricingRules : List.of());
        updated.add(rule);
        return this.withPricingRules(updated).withUpdatedAt(Instant.now());
    }

    public BillingPolicy removeRule(UUID ruleId) {
        List<PricingRule> updated = (pricingRules != null ? pricingRules : List.<PricingRule>of())
                .stream()
                .filter(r -> !r.getId().equals(ruleId))
                .toList();
        return this.withPricingRules(updated).withUpdatedAt(Instant.now());
    }

    public BillingPolicy addPromotion(Promotion promo) {
        List<Promotion> updated = new ArrayList<>(promotions != null ? promotions : List.of());
        updated.add(promo);
        return this.withPromotions(updated).withUpdatedAt(Instant.now());
    }

    /**
     *  — Adds a special surcharge rule to this policy.
     *
     * @param rule the surcharge rule to add
     * @return new policy with the rule appended
     */
    public BillingPolicy addSpecialSurcharge(SpecialSurchargeRule rule) {
        List<SpecialSurchargeRule> updated = new ArrayList<>(
                specialSurcharges != null ? specialSurcharges : List.of());
        updated.add(rule);
        return this.withSpecialSurcharges(updated).withUpdatedAt(Instant.now());
    }

    // ── Domain queries ────────────────────────────────────────────────────────

    public boolean isActive() {
        return PolicyStatus.ACTIVE.equals(status);
    }

    public boolean isValidNow() {
        LocalDate today = LocalDate.now();
        return !today.isBefore(validFrom) && (validTo == null || !today.isAfter(validTo));
    }

    public Optional<PricingRule> findPricingRuleById(UUID ruleId) {
        if (pricingRules == null) return Optional.empty();
        return pricingRules.stream().filter(r -> r.getId().equals(ruleId)).findFirst();
    }

    public List<PricingRule> sortedPricingRules() {
        if (pricingRules == null) return List.of();
        return pricingRules.stream()
                .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                .toList();
    }

    public Optional<CommissionRule> findCommissionRule() {
        if (commissionRules == null || commissionRules.isEmpty()) return Optional.empty();
        return Optional.of(commissionRules.get(0));
    }

    public String currencyCode() {
        if (pricingRules != null && !pricingRules.isEmpty()) {
            Money base = pricingRules.get(0).getBasePrice();
            if (base != null) return base.getCurrency().getCurrencyCode();
        }
        return "XAF";
    }

    /**
     *  — Returns the effective owner type, defaulting to AGENCY for legacy policies.
     */
    public PolicyOwnerType effectiveOwnerType() {
        return ownerType != null ? ownerType : PolicyOwnerType.AGENCY;
    }

    /**
     *  — Returns the effective DSL access level, defaulting to FULL.
     */
    public DslAccessLevel effectiveDslAccessLevel() {
        return dslAccessLevel != null ? dslAccessLevel : DslAccessLevel.FULL;
    }

    /**
     *  — Returns active special surcharge rules only.
     */
    public List<SpecialSurchargeRule> activeSpecialSurcharges() {
        if (specialSurcharges == null) return List.of();
        return specialSurcharges.stream().filter(SpecialSurchargeRule::isActive).toList();
    }

    /**
     *  — Finds the applicable HubStorageRule for the given storage hours and package type.
     *
     * @param storageHours hours in storage
     * @param packageType  type of the stored package (may be null)
     * @return the first matching rule, or empty if none applies
     */
    public Optional<HubStorageRule> findHubStorageRule(int storageHours, PackageType packageType) {
        if (hubStorageRules == null) return Optional.empty();
        return hubStorageRules.stream()
                .filter(r -> r.covers(storageHours))
                .filter(r -> packageType == null || r.appliesTo(packageType))
                .findFirst();
    }

    /**
     *  — Finds applicable network transit rules for the given hop count and route type.
     *
     * @param hopCount  number of relay hops
     * @param interCity whether the route crosses a city boundary
     * @return list of matching transit rules
     */
    public List<NetworkTransitRule> findNetworkTransitRules(int hopCount, boolean interCity) {
        if (networkTransitRules == null) return List.of();
        return networkTransitRules.stream()
                .filter(r -> r.appliesTo(hopCount, interCity))
                .toList();
    }
}
