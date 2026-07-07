package com.yowyob.tiibntick.core.billing.report.application.service;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus;
import com.yowyob.tiibntick.core.billing.report.application.port.in.query.RevenueReportQuery;
import com.yowyob.tiibntick.core.billing.report.application.port.out.*;
import com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReportingService.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    @Mock private InvoiceReportProjectionRepository projectionRepo;
    @Mock private BillingKPISnapshotRepository kpiSnapshotRepo;
    @Mock private ReportExportPort exportPort;

    private ReportingService reportingService;
    private final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reportingService = new ReportingService(projectionRepo, kpiSnapshotRepo, exportPort);
    }

    @Test
    void generateRevenueReport_shouldReturnReportWithCorrectTotals() {
        ReportPeriod period = ReportPeriod.ofCurrentMonth();
        Money xaf1M  = Money.xaf(1_000_000);
        Money xaf750k = Money.xaf(750_000);
        Money xafZero = Money.zero("XAF");

        when(projectionRepo.countByTenantAndPeriodAndStatus(any(), any(), eq(InvoiceStatus.ISSUED)))
                .thenReturn(Mono.just(20L));
        when(projectionRepo.countByTenantAndPeriodAndStatus(any(), any(), eq(InvoiceStatus.PARTIALLY_PAID)))
                .thenReturn(Mono.just(5L));
        when(projectionRepo.countByTenantAndPeriodAndStatus(any(), any(), eq(InvoiceStatus.PAID)))
                .thenReturn(Mono.just(75L));
        when(projectionRepo.countByTenantAndPeriodAndStatus(any(), any(), eq(InvoiceStatus.CANCELLED)))
                .thenReturn(Mono.just(10L));
        when(projectionRepo.countByTenantAndPeriodAndStatus(any(), any(), eq(InvoiceStatus.OVERDUE)))
                .thenReturn(Mono.just(15L));
        when(projectionRepo.sumNetAmountByTenantAndPeriodAndStatus(any(), any(), isNull(), any()))
                .thenReturn(Mono.just(xaf1M));
        when(projectionRepo.sumNetAmountByTenantAndPeriodAndStatus(any(), any(), eq(InvoiceStatus.PAID), any()))
                .thenReturn(Mono.just(xaf750k));
        when(projectionRepo.sumNetAmountByTenantAndPeriodAndStatus(any(), any(), eq(InvoiceStatus.CANCELLED), any()))
                .thenReturn(Mono.just(xafZero));
        when(projectionRepo.sumNetAmountByTenantAndPeriodAndStatus(any(), any(), eq(InvoiceStatus.OVERDUE), any()))
                .thenReturn(Mono.just(xafZero));
        when(projectionRepo.sumTaxByTenantAndPeriod(any(), any(), any()))
                .thenReturn(Mono.just(xafZero));
        when(projectionRepo.revenueByCountry(any(), any(), any()))
                .thenReturn(Flux.empty());

        RevenueReportQuery query = new RevenueReportQuery(TENANT_ID, period, "XAF");

        StepVerifier.create(reportingService.generateRevenueReport(query))
                .assertNext(report -> {
                    assertThat(report.getTenantId()).isEqualTo(TENANT_ID);
                    assertThat(report.getGrossRevenue()).isEqualTo(xaf1M);
                    assertThat(report.getCollectedRevenue()).isEqualTo(xaf750k);
                    assertThat(report.collectionRatePercent()).isCloseTo(75.0, org.assertj.core.data.Offset.offset(0.01));
                })
                .verifyComplete();
    }
}
