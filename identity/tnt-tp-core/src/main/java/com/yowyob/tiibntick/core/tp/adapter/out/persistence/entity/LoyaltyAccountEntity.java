package com.yowyob.tiibntick.core.tp.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC entity for LoyaltyAccount persistence.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_loyalty_accounts")
public class LoyaltyAccountEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;
    @Column("tenant_id") private UUID tenantId;
    @Column("third_party_id") private UUID thirdPartyId;
    @Column("available_points") private int availablePoints;
    @Column("lifetime_points") private int lifetimePoints;
    @Column("redeemed_points") private int redeemedPoints;
    @Column("expired_points") private int expiredPoints;
    @Column("current_tier") private String currentTier;
    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;

    public LoyaltyAccountEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getThirdPartyId() { return thirdPartyId; }
    public void setThirdPartyId(UUID thirdPartyId) { this.thirdPartyId = thirdPartyId; }
    public int getAvailablePoints() { return availablePoints; }
    public void setAvailablePoints(int availablePoints) { this.availablePoints = availablePoints; }
    public int getLifetimePoints() { return lifetimePoints; }
    public void setLifetimePoints(int lifetimePoints) { this.lifetimePoints = lifetimePoints; }
    public int getRedeemedPoints() { return redeemedPoints; }
    public void setRedeemedPoints(int redeemedPoints) { this.redeemedPoints = redeemedPoints; }
    public int getExpiredPoints() { return expiredPoints; }
    public void setExpiredPoints(int expiredPoints) { this.expiredPoints = expiredPoints; }
    public String getCurrentTier() { return currentTier; }
    public void setCurrentTier(String currentTier) { this.currentTier = currentTier; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
