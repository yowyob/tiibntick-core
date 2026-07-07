package com.yowyob.tiibntick.core.billing.report.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * HTTP response for BillingKPISnapshot.
 *
 * @author MANFOUO Braun
 */
public record BillingKPISnapshotResponse(
        UUID id,
        UUID tenantId,
        long openInvoicesCount,
        long overdueInvoicesCount,
        long paidInvoicesToday,
        long generatedInvoicesToday,
        Money outstandingAmount,
        Money collectedToday,
        Money generatedToday,
        double dayCollectionRate,
        double monthToDateCollectionRate,
        Money averageInvoiceValue,
        long averageDaysToPay,
        Instant snapshotAt
) {}
