package com.yowyob.tiibntick.core.billing.invoice.adapter.out.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.billing.invoice.adapter.out.persistence.entity.CreditNoteEntity;
import com.yowyob.tiibntick.core.billing.invoice.adapter.out.persistence.entity.InvoiceEntity;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.*;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.SurchargeLineItem;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.CreditNoteStatus;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Mapper between domain models and R2DBC entities for tnt-billing-invoice.
 *
 * @author MANFOUO Braun
 */
@Component
public class InvoicePersistenceMapper {

    private final ObjectMapper objectMapper;

    public InvoicePersistenceMapper(@Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public InvoiceEntity toEntity(Invoice invoice) {
        InvoiceEntity entity = new InvoiceEntity();
        entity.setId(invoice.getId());
        entity.setInvoiceNumber(invoice.getNumber().value());
        entity.setTenantId(invoice.getTenantId());
        entity.setTenantCode(invoice.getTenantCode());
        entity.setCountryCode(invoice.getCountryCode());
        entity.setMissionId(invoice.getMissionId());
        entity.setSalesOrderId(invoice.getSalesOrderId());
        entity.setClientId(invoice.getClientId());
        entity.setLinesJson(serialize(invoice.getLines()));
        entity.setTaxLinesJson(serialize(invoice.getTaxLines()));
        entity.setDiscountsJson(serialize(invoice.getDiscounts()));
        entity.setSubtotalExTaxAmount(invoice.getSubtotalExTax().amount());
        entity.setSubtotalExTaxCurrency(invoice.getSubtotalExTax().currency());
        entity.setTotalTaxAmount(invoice.getTotalTax().amount());
        entity.setTotalTaxCurrency(invoice.getTotalTax().currency());
        entity.setTotalIncTaxAmount(invoice.getTotalIncTax().amount());
        entity.setTotalIncTaxCurrency(invoice.getTotalIncTax().currency());
        entity.setNetAmountAmount(invoice.getNetAmount().amount());
        entity.setNetAmountCurrency(invoice.getNetAmount().currency());
        entity.setStatus(invoice.getStatus().name());
        entity.setPdfStorageKey(invoice.getPdfStorageKey());
        entity.setIssuedAt(invoice.getIssuedAt() != null ? OffsetDateTime.of(invoice.getIssuedAt(), ZoneOffset.UTC) : null);
        entity.setDueAt(invoice.getDueAt() != null ? OffsetDateTime.of(invoice.getDueAt(), ZoneOffset.UTC) : null);
        entity.setPaidAt(invoice.getPaidAt() != null ? OffsetDateTime.of(invoice.getPaidAt(), ZoneOffset.UTC) : null);
        entity.setCancelledAt(invoice.getCancelledAt() != null ? OffsetDateTime.of(invoice.getCancelledAt(), ZoneOffset.UTC) : null);
        entity.setCancellationReason(invoice.getCancellationReason());
        entity.setCreditNoteRef(invoice.getCreditNoteRef());
        entity.setCreatedAt(invoice.getCreatedAt());
        entity.setUpdatedAt(invoice.getUpdatedAt());
        entity.setVersion(invoice.getVersion());
        //  fields
        entity.setIssuerOrgType(invoice.getIssuerOrgType());
        entity.setIssuerOrgId(invoice.getIssuerOrgId());
        entity.setIssuerTradeName(invoice.getIssuerTradeName());
        entity.setVatApplicable(invoice.getVatApplicable());
        entity.setSurchargeLinesJson(serialize(invoice.getSurchargeLines()));
        entity.setIsFromTemplate(invoice.getIsFromTemplate());
        entity.setAppliedTemplateName(invoice.getAppliedTemplateName());
        return entity;
    }

    public Invoice toDomain(InvoiceEntity entity) {
        List<InvoiceLine> lines = deserialize(entity.getLinesJson(), new TypeReference<>() {});
        List<TaxLine>     taxLines = deserialize(entity.getTaxLinesJson(), new TypeReference<>() {});
        List<InvoiceDiscount> discounts = deserialize(entity.getDiscountsJson(), new TypeReference<>() {});
        List<SurchargeLineItem> surchargeLines =
                deserialize(entity.getSurchargeLinesJson(), new TypeReference<List<SurchargeLineItem>>() {});
        return Invoice.reconstituteFull(
                entity.getId(),
                new InvoiceNumber(entity.getInvoiceNumber()),
                entity.getTenantId(), entity.getTenantCode(), entity.getCountryCode(),
                entity.getMissionId(), entity.getSalesOrderId(), entity.getClientId(),
                lines,
                Money.of(entity.getSubtotalExTaxAmount(), entity.getSubtotalExTaxCurrency()),
                taxLines,
                Money.of(entity.getTotalTaxAmount(), entity.getTotalTaxCurrency()),
                Money.of(entity.getTotalIncTaxAmount(), entity.getTotalIncTaxCurrency()),
                discounts,
                Money.of(entity.getNetAmountAmount(), entity.getNetAmountCurrency()),
                InvoiceStatus.valueOf(entity.getStatus()),
                entity.getPdfStorageKey(),
                entity.getIssuedAt() != null ? entity.getIssuedAt().toLocalDateTime() : null,
                entity.getDueAt() != null ? entity.getDueAt().toLocalDateTime() : null,
                entity.getPaidAt() != null ? entity.getPaidAt().toLocalDateTime() : null,
                entity.getCancelledAt() != null ? entity.getCancelledAt().toLocalDateTime() : null,
                entity.getCancellationReason(), entity.getCreditNoteRef(),
                entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getIssuerOrgType(), entity.getIssuerOrgId(), entity.getIssuerTradeName(),
                entity.getVatApplicable(),
                surchargeLines != null ? surchargeLines : java.util.List.of(),
                entity.getIsFromTemplate(), entity.getAppliedTemplateName());
    }

    public CreditNoteEntity toEntity(CreditNote note) {
        CreditNoteEntity entity = new CreditNoteEntity();
        entity.setId(note.getId());
        entity.setOriginalInvoiceId(note.getOriginalInvoiceId());
        entity.setTenantId(note.getTenantId());
        entity.setAmountValue(note.getAmount().amount());
        entity.setAmountCurrency(note.getAmount().currency());
        entity.setReason(note.getReason());
        entity.setStatus(note.getStatus().name());
        entity.setIssuedAt(note.getIssuedAt() != null ? OffsetDateTime.of(note.getIssuedAt(), ZoneOffset.UTC) : null);
        entity.setAppliedAt(note.getAppliedAt() != null ? OffsetDateTime.of(note.getAppliedAt(), ZoneOffset.UTC) : null);
        return entity;
    }

    public CreditNote toDomain(CreditNoteEntity entity) {
        return CreditNote.reconstitute(
                entity.getId(), entity.getOriginalInvoiceId(), entity.getTenantId(),
                Money.of(entity.getAmountValue(), entity.getAmountCurrency()),
                entity.getReason(), CreditNoteStatus.valueOf(entity.getStatus()),
                entity.getIssuedAt() != null ? entity.getIssuedAt().toLocalDateTime() : null,
                entity.getAppliedAt() != null ? entity.getAppliedAt().toLocalDateTime() : null);
    }

    private <T> String serialize(T obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Serialize error", e); }
    }

    private <T> T deserialize(String json, TypeReference<T> ref) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, ref); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Deserialize error", e); }
    }
}
