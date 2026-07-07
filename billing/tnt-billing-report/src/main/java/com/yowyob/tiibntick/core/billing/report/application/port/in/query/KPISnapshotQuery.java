package com.yowyob.tiibntick.core.billing.report.application.port.in.query;

import java.util.UUID;

/**
 * Query to get or refresh the current billing KPI snapshot for a tenant.
 *
 * @author MANFOUO Braun
 */
public record KPISnapshotQuery(UUID tenantId, String currency) {}
