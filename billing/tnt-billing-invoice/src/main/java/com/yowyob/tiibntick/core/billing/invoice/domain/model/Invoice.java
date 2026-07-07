package com.yowyob.tiibntick.core.billing.invoice.domain.model;

import com.yowyob.tiibntick.core.billing.invoice.domain.event.InvoiceCancelled;
import com.yowyob.tiibntick.core.billing.invoice.domain.event.InvoiceGenerated;
import com.yowyob.tiibntick.core.billing.invoice.domain.event.InvoicePaid;
import com.yowyob.tiibntick.core.billing.invoice.domain.exception.InvoiceStateException;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root: Invoice.
 *
 * <p>Represents a TiiBnTick invoice (AR — Accounts Receivable).
 * Supports the full lifecycle: DRAFT → ISSUED → PAID/OVERDUE/CANCELLED/CREDITED.
 * Multi-country VAT is applied via TaxLine factory methods.
 * PDF generation is triggered via tnt-media-core after issuance.
 * InvoiceNumber format: TNT-FACT-{tenantCode}-{year}-{seq}.</p>
 *
 * <p>Immutable record with domain events collection.</p>
 *
 * @author MANFOUO Braun
 */
public final class Invoice {

    private final UUID id;
    private final InvoiceNumber number;
    private final UUID tenantId;
    private final String tenantCode;
    private final String countryCode;

    /** Reference to the delivery mission (nullable for sales-order invoices). */
    private final String missionId;

    /** Reference to the sales order (nullable for mission invoices). */
    private final String salesOrderId;

    /** The billed client (ThirdParty UUID). */
    private final String clientId;

    private final List<InvoiceLine> lines;
    private final Money subtotalExTax;
    private final List<TaxLine> taxLines;
    private final Money totalTax;
    private final Money totalIncTax;
    private final List<InvoiceDiscount> discounts;
    private final Money netAmount;

    private final InvoiceStatus status;

    /** MinIO storage key for the generated PDF. Populated after PDF generation. */
    private final String pdfStorageKey;

    private final LocalDateTime issuedAt;
    private final LocalDateTime dueAt;
    private final LocalDateTime paidAt;
    private final LocalDateTime cancelledAt;
    private final String cancellationReason;
    private final String creditNoteRef;
    // ── : FreelancerOrg / template issuer context ─────────────────────

    /**
     * Type of the organization issuing this invoice.
     * Values: AGENCY | FREELANCER_ORG | POINT | LINK
     * Null for standard platform-issued invoices.
     */
    private final String issuerOrgType;

    /**
     * UUID of the issuing organization (FreelancerOrg, Agency, etc.).
     * References tnt-organization-core UUID — pure integration key.
     */
    private final String issuerOrgId;

    /**
     * Commercial trade name displayed on the invoice header.
     * For FreelancerOrg: their registered trade name (e.g. "Moto Express Biyem").
     * For Agency: the agency's name. Null for direct platform invoices.
     */
    private final String issuerTradeName;

    /**
     * Whether VAT (TVA) is applicable for this invoice.
     * Based on the issuer's registration status. False for informal/unregistered actors.
     */
    private final Boolean vatApplicable;

    /**
     * Human-readable surcharge line items applied by the billing DSL.
     * Displayed on the PDF invoice for client transparency (FRAGILE, NIGHT, etc.).
     */
    private final List<SurchargeLineItem> surchargeLines;

    /**
     * Whether this invoice was generated from a billing policy template.
     * True = policy was created via tnt-billing-templates (e.g. TPL-FRAGILE).
     */
    private final Boolean isFromTemplate;

    /**
     * Name of the applied billing policy template (e.g. "TPL-FRAGILE").
     * Displayed on invoice for actor self-service auditing.
     * Null when isFromTemplate is false or null.
     */
    private final String appliedTemplateName;

    private final Integer version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private final List<Object> domainEvents;

    private Invoice(
            UUID id, InvoiceNumber number, UUID tenantId, String tenantCode, String countryCode,
            String missionId, String salesOrderId, String clientId,
            List<InvoiceLine> lines, Money subtotalExTax,
            List<TaxLine> taxLines, Money totalTax, Money totalIncTax,
            List<InvoiceDiscount> discounts, Money netAmount,
            InvoiceStatus status, String pdfStorageKey,
            LocalDateTime issuedAt, LocalDateTime dueAt,
            LocalDateTime paidAt, LocalDateTime cancelledAt,
            String cancellationReason, String creditNoteRef,
            Integer version, Instant createdAt, Instant updatedAt,
            List<Object> domainEvents,
            // : FreelancerOrg/template issuer context
            String issuerOrgType, String issuerOrgId, String issuerTradeName,
            Boolean vatApplicable, List<SurchargeLineItem> surchargeLines,
            Boolean isFromTemplate, String appliedTemplateName) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.number = Objects.requireNonNull(number, "number is required");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.tenantCode = Objects.requireNonNull(tenantCode, "tenantCode is required");
        this.countryCode = countryCode != null ? countryCode : "CM";
        this.missionId = missionId;
        this.salesOrderId = salesOrderId;
        this.clientId = Objects.requireNonNull(clientId, "clientId is required");
        this.lines = lines != null ? Collections.unmodifiableList(new ArrayList<>(lines)) : List.of();
        this.subtotalExTax = subtotalExTax;
        this.taxLines = taxLines != null ? Collections.unmodifiableList(new ArrayList<>(taxLines)) : List.of();
        this.totalTax = totalTax;
        this.totalIncTax = totalIncTax;
        this.discounts = discounts != null ? Collections.unmodifiableList(new ArrayList<>(discounts)) : List.of();
        this.netAmount = netAmount;
        this.status = Objects.requireNonNull(status, "status is required");
        this.pdfStorageKey = pdfStorageKey;
        this.issuedAt = issuedAt;
        this.dueAt = dueAt;
        this.paidAt = paidAt;
        this.cancelledAt = cancelledAt;
        this.cancellationReason = cancellationReason;
        this.creditNoteRef = creditNoteRef;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        this.domainEvents = domainEvents != null ? new ArrayList<>(domainEvents) : new ArrayList<>();
        //  fields
        this.issuerOrgType = issuerOrgType;
        this.issuerOrgId = issuerOrgId;
        this.issuerTradeName = issuerTradeName;
        this.vatApplicable = vatApplicable;
        this.surchargeLines = surchargeLines != null
                ? Collections.unmodifiableList(new ArrayList<>(surchargeLines)) : List.of();
        this.isFromTemplate = isFromTemplate;
        this.appliedTemplateName = appliedTemplateName;
    }

    // ─── Factory methods ─────────────────────────────────────────────────────

    /**
     * Creates a new DRAFT invoice from a list of lines.
     * Totals are computed from the lines. VAT is applied per country.
     */
    public static Invoice create(
            InvoiceNumber number, UUID tenantId, String tenantCode, String countryCode,
            String missionId, String salesOrderId, String clientId,
            List<InvoiceLine> lines, List<InvoiceDiscount> discounts,
            String currency) {
        return createWithContext(number, tenantId, tenantCode, countryCode,
                missionId, salesOrderId, clientId, lines, discounts, currency,
                null, null, null, null, List.of(), null, null);
    }

    /**
     * Creates a new DRAFT invoice with full FreelancerOrg/template context ().
     * Totals are computed from the lines. VAT is applied per country.
     */
    public static Invoice createWithContext(
            InvoiceNumber number, UUID tenantId, String tenantCode, String countryCode,
            String missionId, String salesOrderId, String clientId,
            List<InvoiceLine> lines, List<InvoiceDiscount> discounts,
            String currency,
            String issuerOrgType, String issuerOrgId, String issuerTradeName,
            Boolean vatApplicable, List<SurchargeLineItem> surchargeLines,
            Boolean isFromTemplate, String appliedTemplateName) {

        Money subtotal = lines.stream()
                .map(InvoiceLine::lineTotal)
                .reduce(Money.zero(currency), Money::add);

        Money discountTotal = discounts.stream()
                .map(InvoiceDiscount::appliedAmount)
                .reduce(Money.zero(currency), Money::add);

        Money taxableBase = subtotal.subtract(discountTotal);
        TaxLine taxLine = TaxLine.forCountry(countryCode, taxableBase);
        List<TaxLine> taxLines = List.of(taxLine);
        Money totalTax = taxLine.taxAmount();
        Money totalIncTax = taxableBase.add(totalTax);

        Instant now = Instant.now();
        return new Invoice(
                UUID.randomUUID(), number, tenantId, tenantCode, countryCode,
                missionId, salesOrderId, clientId,
                lines, subtotal, taxLines, totalTax, totalIncTax,
                discounts, totalIncTax,
                InvoiceStatus.DRAFT, null,
                null, null, null, null, null, null,
                null, now, now, new ArrayList<>(),
                issuerOrgType, issuerOrgId, issuerTradeName,
                vatApplicable, surchargeLines, isFromTemplate, appliedTemplateName);
    }

    /**
     * Reconstitutes an Invoice from persistence.
     */
    public static Invoice reconstitute(
            UUID id, InvoiceNumber number, UUID tenantId, String tenantCode, String countryCode,
            String missionId, String salesOrderId, String clientId,
            List<InvoiceLine> lines, Money subtotalExTax,
            List<TaxLine> taxLines, Money totalTax, Money totalIncTax,
            List<InvoiceDiscount> discounts, Money netAmount,
            InvoiceStatus status, String pdfStorageKey,
            LocalDateTime issuedAt, LocalDateTime dueAt,
            LocalDateTime paidAt, LocalDateTime cancelledAt,
            String cancellationReason, String creditNoteRef,
            Integer version, Instant createdAt, Instant updatedAt) {
        return reconstituteFull(id, number, tenantId, tenantCode, countryCode,
                missionId, salesOrderId, clientId, lines, subtotalExTax,
                taxLines, totalTax, totalIncTax, discounts, netAmount,
                status, pdfStorageKey, issuedAt, dueAt, paidAt, cancelledAt,
                cancellationReason, creditNoteRef, version, createdAt, updatedAt,
                null, null, null, null, List.of(), null, null);
    }

    /**
     * Reconstitutes an Invoice from persistence — full  fields.
     */
    public static Invoice reconstituteFull(
            UUID id, InvoiceNumber number, UUID tenantId, String tenantCode, String countryCode,
            String missionId, String salesOrderId, String clientId,
            List<InvoiceLine> lines, Money subtotalExTax,
            List<TaxLine> taxLines, Money totalTax, Money totalIncTax,
            List<InvoiceDiscount> discounts, Money netAmount,
            InvoiceStatus status, String pdfStorageKey,
            LocalDateTime issuedAt, LocalDateTime dueAt,
            LocalDateTime paidAt, LocalDateTime cancelledAt,
            String cancellationReason, String creditNoteRef,
            Integer version, Instant createdAt, Instant updatedAt,
            String issuerOrgType, String issuerOrgId, String issuerTradeName,
            Boolean vatApplicable, List<SurchargeLineItem> surchargeLines,
            Boolean isFromTemplate, String appliedTemplateName) {
        return new Invoice(id, number, tenantId, tenantCode, countryCode,
                missionId, salesOrderId, clientId,
                lines, subtotalExTax, taxLines, totalTax, totalIncTax,
                discounts, netAmount, status, pdfStorageKey,
                issuedAt, dueAt, paidAt, cancelledAt,
                cancellationReason, creditNoteRef,
                version, createdAt, updatedAt, new ArrayList<>(),
                issuerOrgType, issuerOrgId, issuerTradeName,
                vatApplicable, surchargeLines, isFromTemplate, appliedTemplateName);
    }

    // ─── Business methods ─────────────────────────────────────────────────────

    /**
     * Issues the invoice (DRAFT → ISSUED), emits InvoiceGenerated.
     *
     * @param dueAt         payment due date
     * @return issued invoice with domain event
     */
    public Invoice issue(LocalDateTime dueAt) {
        if (status != InvoiceStatus.DRAFT) {
            throw new InvoiceStateException(id, "issue", status);
        }
        LocalDateTime now = LocalDateTime.now();
        Invoice issued = new Invoice(
                id, number, tenantId, tenantCode, countryCode,
                missionId, salesOrderId, clientId,
                lines, subtotalExTax, taxLines, totalTax, totalIncTax,
                discounts, netAmount, InvoiceStatus.ISSUED, pdfStorageKey,
                now, dueAt, null, null, null, null,
                version, createdAt, Instant.now(), new ArrayList<>(),
                issuerOrgType, issuerOrgId, issuerTradeName,
                vatApplicable, surchargeLines, isFromTemplate, appliedTemplateName);
        issued.domainEvents.add(new InvoiceGenerated(id, number.value(), missionId, netAmount, tenantId, Instant.now()));
        return issued;
    }

    /**
     * Records a payment reference and marks invoice PAID.
     *
     * @param paymentRef the external payment reference (MoMo ref, Stripe charge ID, etc.)
     * @return paid invoice with InvoicePaid event
     */
    public Invoice markPaid(String paymentRef) {
        if (status != InvoiceStatus.ISSUED
                && status != InvoiceStatus.PARTIALLY_PAID
                && status != InvoiceStatus.OVERDUE) {
            throw new InvoiceStateException(id, "markPaid", status);
        }
        Invoice paid = new Invoice(
                id, number, tenantId, tenantCode, countryCode,
                missionId, salesOrderId, clientId,
                lines, subtotalExTax, taxLines, totalTax, totalIncTax,
                discounts, netAmount, InvoiceStatus.PAID, pdfStorageKey,
                issuedAt, dueAt, LocalDateTime.now(), null, null, null,
                version, createdAt, Instant.now(), new ArrayList<>(),
                issuerOrgType, issuerOrgId, issuerTradeName,
                vatApplicable, surchargeLines, isFromTemplate, appliedTemplateName);
        paid.domainEvents.add(new InvoicePaid(id, paymentRef, netAmount, Instant.now()));
        return paid;
    }

    /**
     * Cancels the invoice with a reason.
     *
     * @param reason the cancellation reason
     * @return cancelled invoice with InvoiceCancelled event
     */
    public Invoice cancel(String reason) {
        if (status == InvoiceStatus.PAID || status == InvoiceStatus.CREDITED) {
            throw new InvoiceStateException(id, "cancel", status);
        }
        Invoice cancelled = new Invoice(
                id, number, tenantId, tenantCode, countryCode,
                missionId, salesOrderId, clientId,
                lines, subtotalExTax, taxLines, totalTax, totalIncTax,
                discounts, netAmount, InvoiceStatus.CANCELLED, pdfStorageKey,
                issuedAt, dueAt, null, LocalDateTime.now(), reason, null,
                version, createdAt, Instant.now(), new ArrayList<>(),
                issuerOrgType, issuerOrgId, issuerTradeName,
                vatApplicable, surchargeLines, isFromTemplate, appliedTemplateName);
        cancelled.domainEvents.add(new InvoiceCancelled(id, reason, Instant.now()));
        return cancelled;
    }

    /**
     * Marks the invoice as overdue (called by scheduler when dueAt is passed).
     *
     * @return overdue invoice copy
     */
    public Invoice markOverdue() {
        if (status != InvoiceStatus.ISSUED && status != InvoiceStatus.PARTIALLY_PAID) {
            throw new InvoiceStateException(id, "markOverdue", status);
        }
        return new Invoice(
                id, number, tenantId, tenantCode, countryCode,
                missionId, salesOrderId, clientId,
                lines, subtotalExTax, taxLines, totalTax, totalIncTax,
                discounts, netAmount, InvoiceStatus.OVERDUE, pdfStorageKey,
                issuedAt, dueAt, null, null, null, null,
                version, createdAt, Instant.now(), new ArrayList<>(),
                issuerOrgType, issuerOrgId, issuerTradeName,
                vatApplicable, surchargeLines, isFromTemplate, appliedTemplateName);
    }

    /**
     * Records the PDF storage key after PDF generation.
     *
     * @param storageKey the MinIO key of the generated PDF
     * @return updated invoice with PDF key
     */
    public Invoice withPdfStorageKey(String storageKey) {
        return new Invoice(
                id, number, tenantId, tenantCode, countryCode,
                missionId, salesOrderId, clientId,
                lines, subtotalExTax, taxLines, totalTax, totalIncTax,
                discounts, netAmount, status, storageKey,
                issuedAt, dueAt, paidAt, cancelledAt, cancellationReason, creditNoteRef,
                version, createdAt, Instant.now(), new ArrayList<>(),
                issuerOrgType, issuerOrgId, issuerTradeName,
                vatApplicable, surchargeLines, isFromTemplate, appliedTemplateName);
    }

    /** Checks if the invoice is overdue. */
    public boolean isOverdue(LocalDateTime now) {
        return dueAt != null && now.isAfter(dueAt)
                && (status == InvoiceStatus.ISSUED || status == InvoiceStatus.PARTIALLY_PAID);
    }

    /** Returns and drains the domain events. */
    public List<Object> collectAndClearEvents() {
        List<Object> events = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return events;
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public InvoiceNumber getNumber() { return number; }
    public UUID getTenantId() { return tenantId; }
    public String getTenantCode() { return tenantCode; }
    public String getCountryCode() { return countryCode; }
    public String getMissionId() { return missionId; }
    public String getSalesOrderId() { return salesOrderId; }
    public String getClientId() { return clientId; }
    public List<InvoiceLine> getLines() { return lines; }
    public Money getSubtotalExTax() { return subtotalExTax; }
    public List<TaxLine> getTaxLines() { return taxLines; }
    public Money getTotalTax() { return totalTax; }
    public Money getTotalIncTax() { return totalIncTax; }
    public List<InvoiceDiscount> getDiscounts() { return discounts; }
    public Money getNetAmount() { return netAmount; }
    public InvoiceStatus getStatus() { return status; }
    public String getPdfStorageKey() { return pdfStorageKey; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public LocalDateTime getDueAt() { return dueAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
    public String getCreditNoteRef() { return creditNoteRef; }
    public Integer getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<Object> getDomainEvents() { return Collections.unmodifiableList(domainEvents); }
    //  getters
    public String getIssuerOrgType() { return issuerOrgType; }
    public String getIssuerOrgId() { return issuerOrgId; }
    public String getIssuerTradeName() { return issuerTradeName; }
    public Boolean getVatApplicable() { return vatApplicable; }
    public List<SurchargeLineItem> getSurchargeLines() { return surchargeLines; }
    public Boolean getIsFromTemplate() { return isFromTemplate; }
    public String getAppliedTemplateName() { return appliedTemplateName; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Invoice that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Invoice{id=" + id + ", number=" + number + ", status=" + status + ", net=" + netAmount + "}";
    }
}
