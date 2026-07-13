package com.yowyob.tiibntick.core.agency.workforce.domain;

import com.yowyob.tiibntick.core.agency.workforce.domain.vo.ContractStatus;
import com.yowyob.tiibntick.core.agency.workforce.domain.vo.ContractType;
import com.yowyob.tiibntick.core.agency.workforce.domain.vo.RemunerationModel;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Ported from tnt-agency {@code Contract}. */
public class Contract {

    private final UUID id;
    private final UUID tenantId;
    private final UUID agencyId;
    private final UUID delivererId;
    private final ContractType contractType;
    private final LocalDate startDate;
    private LocalDate endDate;
    private final RemunerationModel remunerationModel;
    private BigDecimal baseSalary;
    private BigDecimal commissionRate;
    private ContractStatus status;
    private final Instant signedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Contract(UUID id, UUID tenantId, UUID agencyId, UUID delivererId,
                    ContractType contractType, LocalDate startDate, LocalDate endDate,
                    RemunerationModel remunerationModel, BigDecimal baseSalary,
                    BigDecimal commissionRate, ContractStatus status, Instant signedAt,
                    Instant createdAt, Instant updatedAt, long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.agencyId = agencyId;
        this.delivererId = delivererId;
        this.contractType = contractType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.remunerationModel = remunerationModel;
        this.baseSalary = baseSalary;
        this.commissionRate = commissionRate;
        this.status = status;
        this.signedAt = signedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static Contract sign(UUID id, UUID tenantId, UUID agencyId, UUID delivererId,
                                ContractType type, LocalDate start, LocalDate end,
                                RemunerationModel model, BigDecimal salary,
                                BigDecimal rate, Instant now) {
        return new Contract(id, tenantId, agencyId, delivererId, type, start, end,
                model, salary, rate, ContractStatus.ACTIVE, now, now, now, 0L);
    }

    public void terminate(Instant now) {
        this.status = ContractStatus.TERMINATED;
        this.endDate = LocalDate.now();
        this.updatedAt = now;
    }

    public void updateRemuneration(BigDecimal salary, BigDecimal rate, Instant now) {
        if (status != ContractStatus.ACTIVE) {
            throw new IllegalStateException("Only an ACTIVE contract can be updated");
        }
        this.baseSalary = salary;
        this.commissionRate = rate;
        this.updatedAt = now;
    }

    public void renew(LocalDate newEndDate, Instant now) {
        if (status != ContractStatus.ACTIVE) {
            throw new IllegalStateException("Only an ACTIVE contract can be renewed");
        }
        if (newEndDate != null && newEndDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be on or after start date");
        }
        this.endDate = newEndDate;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getDelivererId() { return delivererId; }
    public ContractType getContractType() { return contractType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public RemunerationModel getRemunerationModel() { return remunerationModel; }
    public BigDecimal getBaseSalary() { return baseSalary; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public ContractStatus getStatus() { return status; }
    public Instant getSignedAt() { return signedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
