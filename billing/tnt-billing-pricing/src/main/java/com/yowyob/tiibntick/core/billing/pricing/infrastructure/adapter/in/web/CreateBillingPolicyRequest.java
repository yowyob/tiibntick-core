package com.yowyob.tiibntick.core.billing.pricing.infrastructure.adapter.in.web;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAccessLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Inbound REST DTO for creating a new billing policy.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@link #ownerType} — owner type for multi-owner policies</li>
 *   <li>{@link #ownerActorId} — owner actor UUID string</li>
 *   <li>{@link #dslAccessLevel} — explicit DSL access level override</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public record CreateBillingPolicyRequest(

        @NotBlank String name,
        String description,

        @NotNull UUID tenantId,
        UUID agencyId,

        @NotEmpty @Valid List<PricingRuleRequest> pricingRules,
        @Valid List<SurchargeRuleRequest> surchargeRules,
        @Valid List<LoyaltyRuleRequest> loyaltyRules,

        Boolean isDefault,
        LocalDate validFrom,
        LocalDate validTo,

        // Owner metadata ──────────────────────────────────────────────
        /** Policy owner type. Null = defaults to AGENCY for backward compatibility. */
        PolicyOwnerType ownerType,

        /**
         * UUID string of the policy owner actor.
         * Null for agency-owned policies (agencyId is used instead).
         */
        String ownerActorId,

        /**
         * Explicit DSL access level override.
         * When null, the access level is inferred from ownerType.
         */
        DslAccessLevel dslAccessLevel
) {}
