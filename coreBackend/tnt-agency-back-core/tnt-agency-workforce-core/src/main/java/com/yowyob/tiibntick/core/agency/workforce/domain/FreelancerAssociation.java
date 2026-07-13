package com.yowyob.tiibntick.core.agency.workforce.domain;

import com.yowyob.tiibntick.core.agency.workforce.domain.vo.AssociationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Ported from tnt-agency {@code FreelancerAssociation}. */
public class FreelancerAssociation {

    private final UUID id;
    private final UUID tenantId;
    private final UUID agencyId;
    private final UUID freelancerActorId;
    private BigDecimal commissionRate;
    private final LocalDate startDate;
    private LocalDate endDate;
    private AssociationStatus status;
    private final Instant associatedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public FreelancerAssociation(UUID id, UUID tenantId, UUID agencyId, UUID freelancerActorId,
                                 BigDecimal commissionRate, LocalDate startDate, LocalDate endDate,
                                 AssociationStatus status, Instant associatedAt,
                                 Instant createdAt, Instant updatedAt, long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.agencyId = agencyId;
        this.freelancerActorId = freelancerActorId;
        this.commissionRate = commissionRate;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.associatedAt = associatedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static FreelancerAssociation create(UUID id, UUID tenantId, UUID agencyId,
                                               UUID freelancerActorId, BigDecimal rate,
                                               LocalDate start, Instant now) {
        return new FreelancerAssociation(id, tenantId, agencyId, freelancerActorId,
                rate, start, null, AssociationStatus.ACTIVE, now, now, now, 0L);
    }

    public void end(LocalDate endDate, Instant now) {
        this.status = AssociationStatus.TERMINATED;
        this.endDate = endDate;
        this.updatedAt = now;
    }

    public void pause(Instant now) {
        if (status != AssociationStatus.ACTIVE) {
            throw new IllegalStateException("Only an ACTIVE association can be paused");
        }
        this.status = AssociationStatus.PAUSED;
        this.updatedAt = now;
    }

    public void cancelInvitation(Instant now) {
        if (status != AssociationStatus.PENDING) {
            throw new IllegalStateException("Only a PENDING invitation can be cancelled");
        }
        this.status = AssociationStatus.TERMINATED;
        this.endDate = LocalDate.now();
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getFreelancerActorId() { return freelancerActorId; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public AssociationStatus getStatus() { return status; }
    public Instant getAssociatedAt() { return associatedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
