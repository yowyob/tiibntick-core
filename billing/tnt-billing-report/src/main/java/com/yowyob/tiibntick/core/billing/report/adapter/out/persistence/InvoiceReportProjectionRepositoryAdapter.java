package com.yowyob.tiibntick.core.billing.report.adapter.out.persistence;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus;
import com.yowyob.tiibntick.core.billing.report.adapter.out.persistence.entity.BillingKPISnapshotEntity;
import com.yowyob.tiibntick.core.billing.report.adapter.out.persistence.entity.InvoiceReportEntryEntity;
import com.yowyob.tiibntick.core.billing.report.adapter.out.persistence.r2dbc.BillingKPISnapshotR2dbcRepository;
import com.yowyob.tiibntick.core.billing.report.adapter.out.persistence.r2dbc.InvoiceReportEntryR2dbcRepository;
import com.yowyob.tiibntick.core.billing.report.application.port.out.BillingKPISnapshotRepository;
import com.yowyob.tiibntick.core.billing.report.application.port.out.InvoiceReportProjectionRepository;
import com.yowyob.tiibntick.core.billing.report.domain.model.BillingKPISnapshot;
import com.yowyob.tiibntick.core.billing.report.domain.model.InvoiceReportEntry;
import com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod;
import com.yowyob.tiibntick.core.billing.report.domain.model.RevenueReport.CountryRevenueBreakdown;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Adapter: implements InvoiceReportProjectionRepository using Spring Data R2DBC.
 *
 * @author MANFOUO Braun
 */
@Component
public class InvoiceReportProjectionRepositoryAdapter implements InvoiceReportProjectionRepository {

    private final InvoiceReportEntryR2dbcRepository r2dbcRepo;
    private final DatabaseClient databaseClient;

    public InvoiceReportProjectionRepositoryAdapter(
            InvoiceReportEntryR2dbcRepository r2dbcRepo,
            DatabaseClient databaseClient) {
        this.r2dbcRepo = r2dbcRepo;
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> upsert(InvoiceReportEntry entry) {
        return databaseClient.sql(
                "INSERT INTO tnt_invoice_report_entries " +
                "(invoice_id, invoice_number, tenant_id, country_code, client_id, mission_id, " +
                " gross_amount, tax_amount, net_amount, platform_fee, currency, status, invoice_date, paid_date) " +
                "VALUES (:invoiceId, :invoiceNumber, :tenantId, :countryCode, :clientId, :missionId, " +
                " :grossAmount, :taxAmount, :netAmount, :platformFee, :currency, :status, :invoiceDate, :paidDate) " +
                "ON CONFLICT (invoice_id) DO UPDATE SET " +
                " status = EXCLUDED.status, paid_date = EXCLUDED.paid_date, " +
                " gross_amount = EXCLUDED.gross_amount, net_amount = EXCLUDED.net_amount")
                .bind("invoiceId",     entry.invoiceId())
                .bind("invoiceNumber", entry.invoiceNumber())
                .bind("tenantId",      entry.tenantId())
                .bind("countryCode",   entry.countryCode())
                .bind("clientId",      entry.clientId())
                .bind("missionId",     entry.missionId() != null ? entry.missionId() : "")
                .bind("grossAmount",   entry.grossAmount().amount())
                .bind("taxAmount",     entry.taxAmount().amount())
                .bind("netAmount",     entry.netAmount().amount())
                .bind("platformFee",   entry.platformFee().amount())
                .bind("currency",      entry.netAmount().currency())
                .bind("status",        entry.status().name())
                .bind("invoiceDate",   entry.invoiceDate())
                .bind("paidDate",      entry.paidDate())
                .fetch().rowsUpdated().then();
    }

    @Override
    public Mono<Long> countByTenantAndPeriodAndStatus(UUID tenantId, ReportPeriod period, InvoiceStatus status) {
        return r2dbcRepo.countByTenantPeriodStatus(tenantId, period.from(), period.to(),
                status != null ? status.name() : null);
    }

    @Override
    public Mono<Money> sumNetAmountByTenantAndPeriodAndStatus(
            UUID tenantId, ReportPeriod period, InvoiceStatus status, String currency) {
        return r2dbcRepo.sumNetAmountByTenantPeriodStatus(tenantId, period.from(), period.to(),
                status != null ? status.name() : null)
                .map(amt -> Money.of(amt != null ? amt : BigDecimal.ZERO, currency));
    }

    @Override
    public Mono<BigDecimal> sumTotalSurchargeByOwnerOrgAndPeriod(
            UUID tenantId, ReportPeriod period, String ownerOrgId, String currency) {
        return r2dbcRepo.sumTotalSurchargeByOwnerOrgAndPeriod(tenantId, period.from(), period.to(), ownerOrgId)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    @Override
    public Flux<InvoiceReportEntry> findByFreelancerOrg(
            UUID tenantId, ReportPeriod period, String issuerOrgId) {
        return r2dbcRepo.findByFreelancerOrg(tenantId, period.from(), period.to(), issuerOrgId)
                .map(this::toDomain);
    }

    @Override
    public Flux<InvoiceReportEntry> findByAppliedTemplateName(
            UUID tenantId, ReportPeriod period, String templateName) {
        return r2dbcRepo.findByAppliedTemplateName(tenantId, period.from(), period.to(), templateName)
                .map(this::toDomain);
    }

    @Override
    public Mono<Money> sumTaxByTenantAndPeriod(UUID tenantId, ReportPeriod period, String currency) {
        return r2dbcRepo.sumTaxByTenantPeriod(tenantId, period.from(), period.to())
                .map(amt -> Money.of(amt != null ? amt : BigDecimal.ZERO, currency));
    }

    @Override
    public Flux<CountryRevenueBreakdown> revenueByCountry(UUID tenantId, ReportPeriod period, String currency) {
        return r2dbcRepo.revenueByCountryRaw(tenantId, period.from(), period.to())
                .map(row -> new CountryRevenueBreakdown(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        Money.of(((BigDecimal) row[2]), currency),
                        Money.of(((BigDecimal) row[3]), currency)));
    }

    @Override
    public Flux<InvoiceReportEntry> findByTenantAndPeriod(UUID tenantId, ReportPeriod period) {
        return r2dbcRepo.findByTenantPeriod(tenantId, period.from(), period.to())
                .map(this::toDomain);
    }

    @Override
    public Mono<Double> averageNetAmount(UUID tenantId, ReportPeriod period, String currency) {
        return r2dbcRepo.avgNetAmountByTenantPeriod(tenantId, period.from(), period.to())
                .defaultIfEmpty(0.0);
    }

    @Override
    public Mono<Long> averageDaysToPay(UUID tenantId, ReportPeriod period) {
        return r2dbcRepo.avgDaysToPayByTenantPeriod(tenantId, period.from(), period.to())
                .map(d -> d != null ? d.longValue() : 0L)
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Money> sumPlatformFeeByTenantAndPeriod(UUID tenantId, ReportPeriod period, String currency) {
        return r2dbcRepo.sumPlatformFeeByTenantPeriod(tenantId, period.from(), period.to())
                .map(amt -> Money.of(amt != null ? amt : BigDecimal.ZERO, currency));
    }

    private InvoiceReportEntry toDomain(InvoiceReportEntryEntity e) {
        String cur = e.getCurrency() != null ? e.getCurrency() : "XAF";
        return new InvoiceReportEntry(
                e.getInvoiceId(), e.getInvoiceNumber(), e.getTenantId(),
                e.getCountryCode(), e.getClientId(), e.getMissionId(),
                Money.of(e.getGrossAmount(), cur),
                Money.of(e.getTaxAmount(), cur),
                Money.of(e.getNetAmount(), cur),
                Money.of(e.getPlatformFee(), cur),
                InvoiceStatus.valueOf(e.getStatus()),
                e.getInvoiceDate(), 
                e.getPaidDate(),
                e.getIssuerOrgType(),
                e.getIssuerOrgId(),
                e.getAppliedTemplateName(),
                Money.of(e.getTotalSurchargeAmount() != null ? e.getTotalSurchargeAmount() : BigDecimal.ZERO, cur)
            );
    }
}

/**
 * Adapter: implements BillingKPISnapshotRepository using Spring Data R2DBC.
 *
 * @author MANFOUO Braun
 */
@Component
class BillingKPISnapshotRepositoryAdapter implements BillingKPISnapshotRepository {

    private final BillingKPISnapshotR2dbcRepository r2dbcRepo;

    BillingKPISnapshotRepositoryAdapter(BillingKPISnapshotR2dbcRepository r2dbcRepo) {
        this.r2dbcRepo = r2dbcRepo;
    }

    @Override
    public Mono<BillingKPISnapshot> save(BillingKPISnapshot snapshot) {
        BillingKPISnapshotEntity entity = toEntity(snapshot);
        return r2dbcRepo.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return r2dbcRepo.save(entity);
                })
                .map(e -> snapshot);
    }

    @Override
    public Mono<BillingKPISnapshot> findLatestByTenantId(UUID tenantId) {
        return r2dbcRepo.findLatestByTenantId(tenantId).map(this::toDomain);
    }

    private BillingKPISnapshotEntity toEntity(BillingKPISnapshot s) {
        BillingKPISnapshotEntity e = new BillingKPISnapshotEntity();
        e.setId(s.getId());
        e.setTenantId(s.getTenantId());
        e.setOpenInvoicesCount(s.getOpenInvoicesCount());
        e.setOverdueInvoicesCount(s.getOverdueInvoicesCount());
        e.setPaidInvoicesToday(s.getPaidInvoicesToday());
        e.setGeneratedInvoicesToday(s.getGeneratedInvoicesToday());
        e.setOutstandingAmount(s.getOutstandingAmount().amount());
        e.setCollectedToday(s.getCollectedToday().amount());
        e.setGeneratedToday(s.getGeneratedToday().amount());
        e.setCurrency(s.getOutstandingAmount().currency());
        e.setDayCollectionRate(s.getDayCollectionRate());
        e.setMtdCollectionRate(s.getMonthToDateCollectionRate());
        e.setAvgInvoiceValue(s.getAverageInvoiceValue().amount());
        e.setAvgDaysToPay(s.getAverageDaysToPay());
        e.setSnapshotAt(s.getSnapshotAt());
        return e;
    }

    private BillingKPISnapshot toDomain(BillingKPISnapshotEntity e) {
        String cur = e.getCurrency() != null ? e.getCurrency() : "XAF";
        return BillingKPISnapshot.take(
                e.getTenantId(),
                e.getOpenInvoicesCount(), e.getOverdueInvoicesCount(),
                e.getPaidInvoicesToday(), e.getGeneratedInvoicesToday(),
                Money.of(e.getOutstandingAmount(), cur),
                Money.of(e.getCollectedToday(), cur),
                Money.of(e.getGeneratedToday(), cur),
                e.getDayCollectionRate(), e.getMtdCollectionRate(),
                Money.of(e.getAvgInvoiceValue(), cur),
                e.getAvgDaysToPay());
    }
}
