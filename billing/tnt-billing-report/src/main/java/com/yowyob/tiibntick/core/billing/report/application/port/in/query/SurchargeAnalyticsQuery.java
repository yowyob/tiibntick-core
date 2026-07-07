package com.yowyob.tiibntick.core.billing.report.application.port.in.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Query to generate surcharge analytics for an actor's billing policy.
 *
 * @author MANFOUO Braun
 */
public record SurchargeAnalyticsQuery(
        @NotNull UUID tenantId,
        /** UUID of the owning FreelancerOrg, Agency, or other actor. */
        @NotBlank String ownerOrgId,
        /** Type of the owner: AGENCY | FREELANCER_ORG | POINT | LINK | ADMIN */
        @NotBlank String ownerOrgType,
        @NotNull com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod period,
        @NotBlank String currency
) {}
