package com.yowyob.tiibntick.core.agency.billing.application.mapper;

import com.yowyob.tiibntick.core.agency.billing.adapter.out.persistence.entity.BillingPolicyEntity;
import com.yowyob.tiibntick.core.agency.billing.adapter.out.persistence.entity.InvoiceRecordEntity;
import com.yowyob.tiibntick.core.agency.billing.domain.BillingPolicy;
import com.yowyob.tiibntick.core.agency.billing.domain.InvoiceRecord;
import com.yowyob.tiibntick.core.agency.billing.domain.vo.InvoiceStatus;
import com.yowyob.tiibntick.core.agency.billing.domain.vo.PolicyStatus;

public final class BillingMapper {

    private BillingMapper() {}

    public static BillingPolicyEntity toEntity(BillingPolicy policy) {
        BillingPolicyEntity entity = new BillingPolicyEntity();
        entity.setId(policy.getId());
        entity.setTenantId(policy.getTenantId());
        entity.setAgencyId(policy.getAgencyId());
        entity.setName(policy.getName());
        entity.setDescription(policy.getDescription());
        entity.setStatus(policy.getStatus().name());
        entity.setCurrency(policy.getCurrency());
        entity.setBasePrice(policy.getBasePrice());
        entity.setPricePerKm(policy.getPricePerKm());
        entity.setPricePerKg(policy.getPricePerKg());
        entity.setMinPrice(policy.getMinPrice());
        entity.setCorePolicyId(policy.getCorePolicyId());
        entity.setCreatedAt(policy.getCreatedAt());
        entity.setUpdatedAt(policy.getUpdatedAt());
        entity.setVersion(policy.getVersion());
        return entity;
    }

    public static BillingPolicy toDomain(BillingPolicyEntity entity) {
        return new BillingPolicy(
                entity.getId(), entity.getTenantId(), entity.getAgencyId(),
                entity.getName(), entity.getDescription(),
                PolicyStatus.valueOf(entity.getStatus()), entity.getCurrency(),
                entity.getBasePrice(), entity.getPricePerKm(), entity.getPricePerKg(), entity.getMinPrice(),
                entity.getCorePolicyId(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion() != null ? entity.getVersion() : 0L
        );
    }

    public static InvoiceRecordEntity toEntity(InvoiceRecord invoice) {
        InvoiceRecordEntity entity = new InvoiceRecordEntity();
        entity.setId(invoice.getId());
        entity.setTenantId(invoice.getTenantId());
        entity.setAgencyId(invoice.getAgencyId());
        entity.setMissionId(invoice.getMissionId());
        entity.setReference(invoice.getReference());
        entity.setAmount(invoice.getAmount());
        entity.setCurrency(invoice.getCurrency());
        entity.setStatus(invoice.getStatus().name());
        entity.setCoreInvoiceId(invoice.getCoreInvoiceId());
        entity.setCreatedAt(invoice.getCreatedAt());
        entity.setUpdatedAt(invoice.getUpdatedAt());
        entity.setVersion(invoice.getVersion());
        return entity;
    }

    public static InvoiceRecord toDomain(InvoiceRecordEntity entity) {
        return new InvoiceRecord(
                entity.getId(), entity.getTenantId(), entity.getAgencyId(), entity.getMissionId(),
                entity.getReference(), entity.getAmount(), entity.getCurrency(),
                InvoiceStatus.valueOf(entity.getStatus()), entity.getCoreInvoiceId(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion() != null ? entity.getVersion() : 0L
        );
    }
}
