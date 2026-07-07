package com.yowyob.tiibntick.core.billing.report.domain.model;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FreelancerOrg  billing report domain models.
 *
 * @author MANFOUO Braun
 */
class FreelancerOrgReportTest {

    @Nested
    @DisplayName("FreelancerOrgReport")
    class FreelancerOrgReportTests {

        @Test
        @DisplayName("Should build FreelancerOrgReport with all fields")
        void shouldBuildReport() {
            UUID tenantId = UUID.randomUUID();
            String orgId = "FRL-ORG-001";
            ReportPeriod period = ReportPeriod.ofMonth(YearMonth.of(2026, 1));
            Money revenue = Money.of(new BigDecimal("50000"), "XAF");
            Money commission = Money.of(new BigDecimal("2500"), "XAF");
            Money netRev = Money.of(new BigDecimal("47500"), "XAF");

            FreelancerOrgReport report = FreelancerOrgReport.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .freelancerOrgId(orgId)
                    .tradeName("Moto Express Biyem")
                    .period(period)
                    .totalDeliveries(50L)
                    .completedDeliveries(45L)
                    .cancelledDeliveries(3L)
                    .failedDeliveries(2L)
                    .totalRevenue(revenue)
                    .platformCommission(commission)
                    .netRevenue(netRev)
                    .subDelivererCommissionPaid(Money.zero("XAF"))
                    .orgFinalRevenue(netRev)
                    .subDelivererBreakdowns(List.of())
                    .templatesApplied(3L)
                    .totalSurchargesCollected(Money.of(new BigDecimal("5000"), "XAF"))
                    .generatedAt(java.time.Instant.now())
                    .build();

            assertThat(report.getFreelancerOrgId()).isEqualTo(orgId);
            assertThat(report.getTotalDeliveries()).isEqualTo(50L);
            assertThat(report.getCompletedDeliveries()).isEqualTo(45L);
            assertThat(report.getTotalRevenue().amount()).isEqualByComparingTo("50000");
            assertThat(report.getPlatformCommission().amount()).isEqualByComparingTo("2500");
            assertThat(report.getTemplatesApplied()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("InvoiceReportEntry —  fields")
    class InvoiceReportEntryV3 {

        @Test
        @DisplayName("Should carry FreelancerOrg issuer context")
        void shouldCarryFreelancerOrgContext() {
            var entry = new InvoiceReportEntry(
                    UUID.randomUUID(), "TNT-FACT-001", UUID.randomUUID(), "CM",
                    "CLIENT-001", "MISSION-001",
                    Money.of(new BigDecimal("2300"), "XAF"),
                    Money.of(new BigDecimal("300"), "XAF"),
                    Money.of(new BigDecimal("2000"), "XAF"),
                    Money.of(new BigDecimal("100"), "XAF"),
                    com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus.PAID,
                    java.time.LocalDate.of(2026, 1, 15), null,
                    "FREELANCER_ORG", "FRL-ORG-001",
                    "TPL-FRAGILE",
                    Money.of(new BigDecimal("500"), "XAF"));

            assertThat(entry.issuerOrgType()).isEqualTo("FREELANCER_ORG");
            assertThat(entry.issuerOrgId()).isEqualTo("FRL-ORG-001");
            assertThat(entry.appliedTemplateName()).isEqualTo("TPL-FRAGILE");
            assertThat(entry.totalSurchargeAmount().amount()).isEqualByComparingTo("500");
        }

        @Test
        @DisplayName("Standard invoice entry should have null FreelancerOrg context")
        void standardEntryHasNullContext() {
            var entry = new InvoiceReportEntry(
                    UUID.randomUUID(), "TNT-FACT-002", UUID.randomUUID(), "CM",
                    "CLIENT-002", "MISSION-002",
                    Money.of(new BigDecimal("1000"), "XAF"),
                    Money.of(new BigDecimal("200"), "XAF"),
                    Money.of(new BigDecimal("800"), "XAF"),
                    Money.of(new BigDecimal("40"), "XAF"),
                    com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus.ISSUED,
                    java.time.LocalDate.of(2026, 1, 10), null,
                    null, null, null, null);

            assertThat(entry.issuerOrgType()).isNull();
            assertThat(entry.issuerOrgId()).isNull();
            assertThat(entry.appliedTemplateName()).isNull();
        }
    }

    @Nested
    @DisplayName("SurchargeAnalyticsReport")
    class SurchargeAnalyticsTests {

        @Test
        @DisplayName("Should build SurchargeAnalyticsReport")
        void shouldBuildSurchargeReport() {
            SurchargeAnalyticsReport report = SurchargeAnalyticsReport.builder()
                    .id(UUID.randomUUID())
                    .tenantId(UUID.randomUUID())
                    .ownerOrgId("FRL-ORG-001")
                    .ownerOrgType("FREELANCER_ORG")
                    .period(ReportPeriod.ofMonth(YearMonth.of(2026, 1)))
                    .totalSurchargesTriggered(30L)
                    .totalDeliveriesWithSurcharge(25L)
                    .totalSurchargeRevenue(Money.of(new BigDecimal("15000"), "XAF"))
                    .averageSurchargePerDelivery(600.0)
                    .surchargeBreakdowns(List.of())
                    .generatedAt(java.time.Instant.now())
                    .build();

            assertThat(report.getOwnerOrgId()).isEqualTo("FRL-ORG-001");
            assertThat(report.getTotalSurchargesTriggered()).isEqualTo(30L);
        }
    }
}
