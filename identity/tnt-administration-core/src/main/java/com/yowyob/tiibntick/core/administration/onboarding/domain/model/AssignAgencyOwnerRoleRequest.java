package com.yowyob.tiibntick.core.administration.onboarding.domain.model;

import java.util.UUID;

/**
 * Request body for {@code POST .../assign-owner-role} — Phase 2b of agency onboarding:
 * assigns the Kernel administration role identified by {@code roleCode} (defaults to
 * {@code "AGENCY_ADMIN"} when {@code null}) to {@code ownerUserId}, scoped to
 * {@code kernelOrganizationId}. See {@code CORE_KERNEL_GATEWAY_SPEC.md} §8 step 6-7.
 *
 * @author MANFOUO Braun
 */
public record AssignAgencyOwnerRoleRequest(
        UUID kernelOrganizationId,
        UUID ownerUserId,
        String roleCode
) {
}
