package com.yowyob.tiibntick.core.billing.report.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * R2DBC entity for the invoice report projection table.
 * One row per invoice, updated on each state change event.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_invoice_report_entries")
public class InvoiceReportEntryEntity {

    @Id
    @Column("invoice_id")       private UUID invoiceId;
    @Column("invoice_number")   private String invoiceNumber;
    @Column("tenant_id")        private UUID tenantId;
    @Column("country_code")     private String countryCode;
    @Column("client_id")        private String clientId;
    @Column("mission_id")       private String missionId;

    @Column("gross_amount")     private BigDecimal grossAmount;
    @Column("tax_amount")       private BigDecimal taxAmount;
    @Column("net_amount")       private BigDecimal netAmount;
    @Column("platform_fee")     private BigDecimal platformFee;
    @Column("currency")         private String currency;

    @Column("status")           private String status;
    @Column("invoice_date")     private LocalDate invoiceDate;
    @Column("paid_date")        private LocalDate paidDate;
    @Column("issuer_org_type") private String issuerOrgType;
    @Column("issuer_org_id")   private String issuerOrgId;
    @Column("applied_template_name") private String appliedTemplateName;
    @Column("total_surcharge_amount") private BigDecimal totalSurchargeAmount;

    public InvoiceReportEntryEntity() {}

    public UUID getInvoiceId() { return invoiceId; }
    public void setInvoiceId(UUID invoiceId) { this.invoiceId = invoiceId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getMissionId() { return missionId; }
    public void setMissionId(String missionId) { this.missionId = missionId; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
    public BigDecimal getPlatformFee() { return platformFee; }
    public void setPlatformFee(BigDecimal platformFee) { this.platformFee = platformFee; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }
    public LocalDate getPaidDate() { return paidDate; }
    public void setPaidDate(LocalDate paidDate) { this.paidDate = paidDate; }
    //  getters/setters
    public String getIssuerOrgType() { return issuerOrgType; }
    public void setIssuerOrgType(String v) { this.issuerOrgType = v; }
    public String getIssuerOrgId() { return issuerOrgId; }
    public void setIssuerOrgId(String v) { this.issuerOrgId = v; }
    public String getAppliedTemplateName() { return appliedTemplateName; }
    public void setAppliedTemplateName(String v) { this.appliedTemplateName = v; }
    public java.math.BigDecimal getTotalSurchargeAmount() { return totalSurchargeAmount; }
    public void setTotalSurchargeAmount(java.math.BigDecimal v) { this.totalSurchargeAmount = v; }
}
