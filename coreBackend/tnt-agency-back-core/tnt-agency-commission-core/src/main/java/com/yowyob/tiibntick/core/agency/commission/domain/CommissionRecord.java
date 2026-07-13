package com.yowyob.tiibntick.core.agency.commission.domain;

import com.yowyob.tiibntick.core.agency.commission.domain.vo.CommissionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Ported from tnt-agency {@code CommissionRecord}. */
public class CommissionRecord {

    private final UUID id;
    private final UUID tenantId;
    private final UUID agencyId;
    private final UUID delivererId;
    private final UUID missionId;
    private final BigDecimal amount;
    private final String currency;
    private CommissionStatus status;
    private String disputeReason;
    private Instant paidAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public CommissionRecord(UUID id, UUID tenantId, UUID agencyId, UUID delivererId,
                            UUID missionId, BigDecimal amount, String currency,
                            CommissionStatus status, String disputeReason, Instant paidAt,
                            Instant createdAt, Instant updatedAt, long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.agencyId = agencyId;
        this.delivererId = delivererId;
        this.missionId = missionId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.disputeReason = disputeReason;
        this.paidAt = paidAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static CommissionRecord create(UUID id, UUID tenantId, UUID agencyId,
                                          UUID delivererId, UUID missionId,
                                          BigDecimal amount, String currency, Instant now) {
        return new CommissionRecord(id, tenantId, agencyId, delivererId, missionId,
                amount, currency, CommissionStatus.CALCULATED, null, null, now, now, 0L);
    }

    public void pay(Instant now) {
        if (status == CommissionStatus.DISPUTED) {
            throw new IllegalStateException("A disputed commission cannot be paid directly");
        }
        if (status != CommissionStatus.VALIDATED) {
            throw new IllegalStateException("Commission must be VALIDATED before payment");
        }
        this.status = CommissionStatus.PAID;
        this.paidAt = now;
        this.updatedAt = now;
    }

    public void validate(Instant now) {
        if (status != CommissionStatus.CALCULATED) {
            throw new IllegalStateException("Only a CALCULATED commission can be validated");
        }
        this.status = CommissionStatus.VALIDATED;
        this.updatedAt = now;
    }

    public void dispute(String reason, Instant now) {
        this.status = CommissionStatus.DISPUTED;
        this.disputeReason = reason;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getDelivererId() { return delivererId; }
    public UUID getMissionId() { return missionId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public CommissionStatus getStatus() { return status; }
    public String getDisputeReason() { return disputeReason; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
