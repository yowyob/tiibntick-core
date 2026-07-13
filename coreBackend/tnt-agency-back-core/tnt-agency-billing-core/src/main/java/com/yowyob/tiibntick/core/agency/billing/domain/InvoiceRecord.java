package com.yowyob.tiibntick.core.agency.billing.domain;

import com.yowyob.tiibntick.common.domain.model.TntBaseEntity;
import com.yowyob.tiibntick.core.agency.billing.domain.vo.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class InvoiceRecord extends TntBaseEntity {

    private final UUID agencyId;
    private final UUID missionId;
    private final String reference;
    private final BigDecimal amount;
    private final String currency;
    private InvoiceStatus status;
    private final UUID coreInvoiceId;

    public InvoiceRecord(UUID id, UUID tenantId, UUID agencyId, UUID missionId, String reference,
                         BigDecimal amount, String currency, InvoiceStatus status, UUID coreInvoiceId,
                         Instant createdAt, Instant updatedAt, long version) {
        super(id, tenantId, createdAt, updatedAt, version);
        this.agencyId = agencyId;
        this.missionId = missionId;
        this.reference = reference;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.coreInvoiceId = coreInvoiceId;
    }

    public static InvoiceRecord generate(UUID id, UUID tenantId, UUID agencyId,
                                         UUID missionId, String reference,
                                         BigDecimal amount, String currency,
                                         UUID coreInvoiceId, Instant now) {
        return new InvoiceRecord(
                id, tenantId, agencyId, missionId, reference, amount, currency,
                InvoiceStatus.GENERATED, coreInvoiceId, now, now, 0L
        );
    }

    public UUID getAgencyId() { return agencyId; }
    public UUID getMissionId() { return missionId; }
    public String getReference() { return reference; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public InvoiceStatus getStatus() { return status; }
    public UUID getCoreInvoiceId() { return coreInvoiceId; }
}
