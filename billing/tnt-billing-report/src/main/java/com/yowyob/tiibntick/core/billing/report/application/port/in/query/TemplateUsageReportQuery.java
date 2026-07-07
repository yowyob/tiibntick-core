package com.yowyob.tiibntick.core.billing.report.application.port.in.query;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Query to generate a billing template usage analytics report.
 *
 * @author MANFOUO Braun
 */
public record TemplateUsageReportQuery(
        @NotNull UUID tenantId,
        /** Template code filter (null = report on all templates). */
        String templateCode,
        @NotNull com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod period,
        @NotNull String currency
) {}
