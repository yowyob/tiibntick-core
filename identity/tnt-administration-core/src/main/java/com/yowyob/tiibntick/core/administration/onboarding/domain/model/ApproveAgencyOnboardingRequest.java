package com.yowyob.tiibntick.core.administration.onboarding.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST .../approve} — the recommended, single-call orchestration:
 * provisions the Kernel organization (Phase 2), creates the TiiBnTick {@code Agency},
 * provisions the 9 canonical TNT role templates, initializes platform options, and
 * (if {@code ownerUserId} is present) assigns the owner's Kernel administration role.
 * See {@code CORE_KERNEL_GATEWAY_SPEC.md} §8.4.
 *
 * @param ownerUserId  optional — when present, Phase 2b (role assignment) runs too
 * @param ownerRoleCode optional — defaults to {@code "AGENCY_ADMIN"}
 */
public record ApproveAgencyOnboardingRequest(
        UUID tenantId,
        UUID businessActorId,
        String code,
        String service,
        String shortName,
        String longName,
        String email,
        String businessRegistrationNumber,
        String primaryCurrency,
        boolean provisionCommercial,
        String commercialPlanCode,
        List<String> serviceCodes,
        String approvalReason,
        UUID ownerUserId,
        String ownerRoleCode
) {
}
