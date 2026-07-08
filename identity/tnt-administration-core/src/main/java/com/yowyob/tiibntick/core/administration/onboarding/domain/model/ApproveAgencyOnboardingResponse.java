package com.yowyob.tiibntick.core.administration.onboarding.domain.model;

import java.util.UUID;

/**
 * Response body for {@code POST .../approve} — see
 * {@code CORE_KERNEL_GATEWAY_SPEC.md} §8.4.
 *
 * @author MANFOUO Braun
 */
public record ApproveAgencyOnboardingResponse(
        UUID kernelOrganizationId,
        UUID kernelBusinessActorId,
        UUID coreAgencyId,
        boolean tntRolesProvisioned,
        boolean platformOptionsInitialized,
        UUID ownerRoleAssignmentId
) {
}
