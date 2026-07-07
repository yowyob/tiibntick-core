package com.yowyob.tiibntick.core.billing.invoice.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * R2DBC entity for Invoice persistence.
 * Lines, TaxLines, and Discounts are stored as JSONB for simplicity.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_invoices")
public class InvoiceEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("invoice_number") private String invoiceNumber;
    @Column("tenant_id")      private UUID tenantId;
    @Column("tenant_code")    private String tenantCode;
    @Column("country_code")   private String countryCode;
    @Column("mission_id")     private String missionId;
    @Column("sales_order_id") private String salesOrderId;
    @Column("client_id")      private String clientId;

    @Column("lines_json")     private String linesJson;
    @Column("tax_lines_json") private String taxLinesJson;
    @Column("discounts_json") private String discountsJson;

    @Column("subtotal_ex_tax_amount")   private BigDecimal subtotalExTaxAmount;
    @Column("subtotal_ex_tax_currency") private String subtotalExTaxCurrency;
    @Column("total_tax_amount")         private BigDecimal totalTaxAmount;
    @Column("total_tax_currency")       private String totalTaxCurrency;
    @Column("total_inc_tax_amount")     private BigDecimal totalIncTaxAmount;
    @Column("total_inc_tax_currency")   private String totalIncTaxCurrency;
    @Column("net_amount_amount")        private BigDecimal netAmountAmount;
    @Column("net_amount_currency")      private String netAmountCurrency;

    @Column("status")           private String status;
    @Column("pdf_storage_key")  private String pdfStorageKey;
    @Column("issued_at")        private OffsetDateTime issuedAt;
    @Column("due_at")           private OffsetDateTime dueAt;
    @Column("paid_at")          private OffsetDateTime paidAt;
    @Column("cancelled_at")     private OffsetDateTime cancelledAt;
    @Column("cancellation_reason") private String cancellationReason;
    @Column("credit_note_ref")  private String creditNoteRef;

    // : FreelancerOrg issuer context
    @Column("issuer_org_type")       private String issuerOrgType;
    @Column("issuer_org_id")         private String issuerOrgId;
    @Column("issuer_trade_name")     private String issuerTradeName;
    @Column("vat_applicable")        private Boolean vatApplicable;
    @Column("surcharge_lines_json")  private String surchargeLinesJson;
    @Column("is_from_template")      private Boolean isFromTemplate;
    @Column("applied_template_name") private String appliedTemplateName;

    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;

    @Version
    private Integer version;

    public InvoiceEntity() {}

    // --- Getters and setters ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getMissionId() { return missionId; }
    public void setMissionId(String missionId) { this.missionId = missionId; }
    public String getSalesOrderId() { return salesOrderId; }
    public void setSalesOrderId(String salesOrderId) { this.salesOrderId = salesOrderId; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getLinesJson() { return linesJson; }
    public void setLinesJson(String linesJson) { this.linesJson = linesJson; }
    public String getTaxLinesJson() { return taxLinesJson; }
    public void setTaxLinesJson(String taxLinesJson) { this.taxLinesJson = taxLinesJson; }
    public String getDiscountsJson() { return discountsJson; }
    public void setDiscountsJson(String discountsJson) { this.discountsJson = discountsJson; }
    public BigDecimal getSubtotalExTaxAmount() { return subtotalExTaxAmount; }
    public void setSubtotalExTaxAmount(BigDecimal v) { this.subtotalExTaxAmount = v; }
    public String getSubtotalExTaxCurrency() { return subtotalExTaxCurrency; }
    public void setSubtotalExTaxCurrency(String v) { this.subtotalExTaxCurrency = v; }
    public BigDecimal getTotalTaxAmount() { return totalTaxAmount; }
    public void setTotalTaxAmount(BigDecimal v) { this.totalTaxAmount = v; }
    public String getTotalTaxCurrency() { return totalTaxCurrency; }
    public void setTotalTaxCurrency(String v) { this.totalTaxCurrency = v; }
    public BigDecimal getTotalIncTaxAmount() { return totalIncTaxAmount; }
    public void setTotalIncTaxAmount(BigDecimal v) { this.totalIncTaxAmount = v; }
    public String getTotalIncTaxCurrency() { return totalIncTaxCurrency; }
    public void setTotalIncTaxCurrency(String v) { this.totalIncTaxCurrency = v; }
    public BigDecimal getNetAmountAmount() { return netAmountAmount; }
    public void setNetAmountAmount(BigDecimal v) { this.netAmountAmount = v; }
    public String getNetAmountCurrency() { return netAmountCurrency; }
    public void setNetAmountCurrency(String v) { this.netAmountCurrency = v; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPdfStorageKey() { return pdfStorageKey; }
    public void setPdfStorageKey(String pdfStorageKey) { this.pdfStorageKey = pdfStorageKey; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public void setDueAt(OffsetDateTime dueAt) { this.dueAt = dueAt; }
    public OffsetDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(OffsetDateTime paidAt) { this.paidAt = paidAt; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(OffsetDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public String getCreditNoteRef() { return creditNoteRef; }
    public void setCreditNoteRef(String creditNoteRef) { this.creditNoteRef = creditNoteRef; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    //  getters/setters
    public String getIssuerOrgType() { return issuerOrgType; }
    public void setIssuerOrgType(String v) { this.issuerOrgType = v; }
    public String getIssuerOrgId() { return issuerOrgId; }
    public void setIssuerOrgId(String v) { this.issuerOrgId = v; }
    public String getIssuerTradeName() { return issuerTradeName; }
    public void setIssuerTradeName(String v) { this.issuerTradeName = v; }
    public Boolean getVatApplicable() { return vatApplicable; }
    public void setVatApplicable(Boolean v) { this.vatApplicable = v; }
    public String getSurchargeLinesJson() { return surchargeLinesJson; }
    public void setSurchargeLinesJson(String v) { this.surchargeLinesJson = v; }
    public Boolean getIsFromTemplate() { return isFromTemplate; }
    public void setIsFromTemplate(Boolean v) { this.isFromTemplate = v; }
    public String getAppliedTemplateName() { return appliedTemplateName; }
    public void setAppliedTemplateName(String v) { this.appliedTemplateName = v; }
}
