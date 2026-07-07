package com.yowyob.tiibntick.core.billing.report.application.port.in.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Query to generate a financial report for a specific FreelancerOrganization.
 *
 * @author MANFOUO Braun
 */
public record FreelancerOrgReportQuery(
        @NotNull UUID tenantId,
        /** UUID of the FreelancerOrg (from tnt-organization-core). */
        @NotBlank String freelancerOrgId,
        @NotNull com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod period,
        @NotBlank String currency
) {}
