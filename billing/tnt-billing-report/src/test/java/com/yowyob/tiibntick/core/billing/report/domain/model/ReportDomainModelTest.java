package com.yowyob.tiibntick.core.billing.report.domain.model;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for tnt-billing-report domain models.
 *
 * @author MANFOUO Braun
 */
class ReportDomainModelTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    void reportPeriod_ofMonth_shouldBeCorrect() {
        ReportPeriod period = ReportPeriod.ofMonth(YearMonth.of(2026, 5));
        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(period.to()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(period.daysCount()).isEqualTo(31);
    }

    @Test
    void reportPeriod_invalidRange_shouldThrow() {
        assertThatThrownBy(() -> ReportPeriod.of(
                LocalDate.of(2026, 5, 31), LocalDate.of(2026, 5, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revenueReport_collectionRatePercent_shouldBeCorrect() {
        ReportPeriod period = ReportPeriod.ofCurrentMonth();
        Money gross     = Money.xaf(1_000_000);
        Money collected = Money.xaf(750_000);
        Money zero      = Money.zero("XAF");

        RevenueReport report = RevenueReport.of(TENANT_ID, period,
                100, 75, 10, 15,
                gross, collected, zero, zero, zero, collected, List.of());

        assertThat(report.collectionRatePercent()).isCloseTo(75.0, within(0.01));
    }

    @Test
    void revenueReport_withZeroGross_shouldReturnZeroCollectionRate() {
        ReportPeriod period = ReportPeriod.ofCurrentMonth();
        Money zero = Money.zero("XAF");

        RevenueReport report = RevenueReport.of(TENANT_ID, period,
                0, 0, 0, 0, zero, zero, zero, zero, zero, zero, List.of());

        assertThat(report.collectionRatePercent()).isZero();
    }

    @Test
    void marginReport_shouldComputeGrossAndNetMargin() {
        ReportPeriod period = ReportPeriod.ofCurrentMonth();
        Money revenue      = Money.xaf(500_000);
        Money costs        = Money.xaf(200_000);
        Money platformFees = Money.xaf(25_000);

        MarginReport report = MarginReport.of(TENANT_ID, period, revenue, costs, platformFees, List.of());

        assertThat(report.getGrossMargin().amount()).isEqualByComparingTo(
                revenue.subtract(costs).amount());
        assertThat(report.getGrossMarginPercent()).isCloseTo(60.0, within(0.01));
        assertThat(report.getNetMargin().amount()).isEqualByComparingTo(
                revenue.subtract(costs).subtract(platformFees).amount());
    }

    @Test
    void billingKPISnapshot_shouldInitializeCorrectly() {
        Money outstanding = Money.xaf(500_000);
        Money collected   = Money.xaf(50_000);
        Money generated   = Money.xaf(100_000);
        Money avgInvoice  = Money.xaf(15_000);

        BillingKPISnapshot snapshot = BillingKPISnapshot.take(
                TENANT_ID, 30, 5, 3, 7,
                outstanding, collected, generated,
                50.0, 65.0, avgInvoice, 3L);

        assertThat(snapshot.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(snapshot.getOpenInvoicesCount()).isEqualTo(30);
        assertThat(snapshot.getOverdueInvoicesCount()).isEqualTo(5);
        assertThat(snapshot.getDayCollectionRate()).isEqualTo(50.0);
        assertThat(snapshot.getSnapshotAt()).isNotNull();
    }
}
