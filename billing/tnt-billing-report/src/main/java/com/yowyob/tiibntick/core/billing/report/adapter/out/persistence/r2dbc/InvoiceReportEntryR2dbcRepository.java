package com.yowyob.tiibntick.core.billing.report.adapter.out.persistence.r2dbc;

import com.yowyob.tiibntick.core.billing.report.adapter.out.persistence.entity.InvoiceReportEntryEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Spring Data R2DBC repository for InvoiceReportEntryEntity.
 *
 * @author MANFOUO Braun
 */
public interface InvoiceReportEntryR2dbcRepository
        extends ReactiveCrudRepository<InvoiceReportEntryEntity, UUID> {

    @Query("SELECT COUNT(*) FROM tnt_invoice_report_entries " +
           "WHERE tenant_id = :tenantId AND invoice_date BETWEEN :from AND :to AND status = :status")
    Mono<Long> countByTenantPeriodStatus(UUID tenantId, LocalDate from, LocalDate to, String status);

    @Query("SELECT COALESCE(SUM(net_amount), 0) FROM tnt_invoice_report_entries " +
           "WHERE tenant_id = :tenantId AND invoice_date BETWEEN :from AND :to AND (:status IS NULL OR status = :status)")
    Mono<java.math.BigDecimal> sumNetAmountByTenantPeriodStatus(UUID tenantId, LocalDate from, LocalDate to, String status);

    @Query("SELECT COALESCE(SUM(tax_amount), 0) FROM tnt_invoice_report_entries " +
           "WHERE tenant_id = :tenantId AND invoice_date BETWEEN :from AND :to")
    Mono<java.math.BigDecimal> sumTaxByTenantPeriod(UUID tenantId, LocalDate from, LocalDate to);

    @Query("SELECT COALESCE(SUM(platform_fee), 0) FROM tnt_invoice_report_entries " +
           "WHERE tenant_id = :tenantId AND invoice_date BETWEEN :from AND :to")
    Mono<java.math.BigDecimal> sumPlatformFeeByTenantPeriod(UUID tenantId, LocalDate from, LocalDate to);

    @Query("SELECT COALESCE(AVG(net_amount), 0) FROM tnt_invoice_report_entries " +
           "WHERE tenant_id = :tenantId AND invoice_date BETWEEN :from AND :to")
    Mono<Double> avgNetAmountByTenantPeriod(UUID tenantId, LocalDate from, LocalDate to);

    @Query("SELECT COALESCE(AVG(paid_date - invoice_date), 0) " +
           "FROM tnt_invoice_report_entries " +
           "WHERE tenant_id = :tenantId AND invoice_date BETWEEN :from AND :to AND paid_date IS NOT NULL")
    Mono<Double> avgDaysToPayByTenantPeriod(UUID tenantId, LocalDate from, LocalDate to);

    @Query("SELECT country_code, COUNT(*) as invoice_count, " +
           "SUM(net_amount) as revenue, SUM(tax_amount) as tax_amount " +
           "FROM tnt_invoice_report_entries " +
           "WHERE tenant_id = :tenantId AND invoice_date BETWEEN :from AND :to " +
           "GROUP BY country_code ORDER BY revenue DESC")
    Flux<Object[]> revenueByCountryRaw(UUID tenantId, LocalDate from, LocalDate to);

    @Query("SELECT * FROM tnt_invoice_report_entries " +
           "WHERE tenant_id = :tenantId AND invoice_date BETWEEN :from AND :to ORDER BY invoice_date DESC")
    Flux<InvoiceReportEntryEntity> findByTenantPeriod(UUID tenantId, LocalDate from, LocalDate to);

    @Query("SELECT COALESCE(SUM(total_surcharge_amount), 0) FROM tnt_invoice_report_entries " +
           "WHERE tenant_id = :tenantId AND invoice_date BETWEEN :from AND :to AND issuer_org_id = :ownerOrgId")
    Mono<java.math.BigDecimal> sumTotalSurchargeByOwnerOrgAndPeriod(UUID tenantId, LocalDate from, LocalDate to, String ownerOrgId);

    @Query("SELECT * FROM tnt_invoice_report_entries " +
           "WHERE tenant_id = :tenantId AND invoice_date BETWEEN :from AND :to AND issuer_org_id = :issuerOrgId ORDER BY invoice_date DESC")
    Flux<InvoiceReportEntryEntity> findByFreelancerOrg(UUID tenantId, LocalDate from, LocalDate to, String issuerOrgId);

    @Query("SELECT * FROM tnt_invoice_report_entries " +
           "WHERE tenant_id = :tenantId AND invoice_date BETWEEN :from AND :to AND applied_template_name = :templateName ORDER BY invoice_date DESC")
    Flux<InvoiceReportEntryEntity> findByAppliedTemplateName(UUID tenantId, LocalDate from, LocalDate to, String templateName);
}
