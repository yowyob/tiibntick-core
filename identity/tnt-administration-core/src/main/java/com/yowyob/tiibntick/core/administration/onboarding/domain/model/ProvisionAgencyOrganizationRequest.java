package com.yowyob.tiibntick.core.administration.onboarding.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST .../provision} — Phase 2 of agency onboarding: creates and
 * approves the Kernel {@code Organization}, and optionally subscribes it to a commercial
 * plan and services. See {@code CORE_KERNEL_GATEWAY_SPEC.md} §8.3.
 *
 * <p><b>Field set verified against the live Kernel schema</b> ({@code CreateOrganizationRequest}
 * in {@code docs/kernel-api/schemas.md}) rather than copied from the spec's illustrative
 * JSON, which was stale: the Kernel requires {@code businessActorId}/{@code code}/
 * {@code service}/{@code shortName}/{@code longName} — there is no {@code organizationType}
 * or {@code legalName} field on the live endpoint.
 *
 * @param service required Kernel business-domain classifier for the organization
 *                (e.g. {@code "LOGISTICS"} — Kernel-defined, not documented as an enum in
 *                its OpenAPI spec at the time of writing; pass whatever value the Kernel
 *                team confirms for TiiBnTick agencies)
 */
public record ProvisionAgencyOrganizationRequest(
        UUID tenantId,
        UUID businessActorId,
        String code,
        String service,
        String shortName,
        String longName,
        String email,
        String businessRegistrationNumber,
        boolean provisionCommercial,
        String commercialPlanCode,
        List<String> serviceCodes,
        String approvalReason
) {
}
