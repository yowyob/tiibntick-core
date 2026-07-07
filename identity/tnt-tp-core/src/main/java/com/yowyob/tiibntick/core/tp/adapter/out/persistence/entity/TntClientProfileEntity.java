package com.yowyob.tiibntick.core.tp.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC entity for TntClientProfile persistence.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_client_profiles")
public class TntClientProfileEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("third_party_id")
    private UUID thirdPartyId;

    @Column("tnt_roles")
    private String tntRoles;

    @Column("kyc_status")
    private String kycStatus;

    @Column("phone_alias")
    private String phoneAlias;

    @Column("phone_masked")
    private boolean phoneMasked;

    @Column("average_rating")
    private Double averageRating;

    @Column("rating_count")
    private int ratingCount;

    @Column("total_deliveries")
    private int totalDeliveries;

    @Column("preferred_locale")
    private String preferredLocale;

    @Column("preferred_currency")
    private String preferredCurrency;

    @Column("loyalty_tier")
    private String loyaltyTier;

    @Column("provider_links_json")
    private String providerLinksJson;

    @Column("active")
    private boolean active;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    public TntClientProfileEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getThirdPartyId() { return thirdPartyId; }
    public void setThirdPartyId(UUID thirdPartyId) { this.thirdPartyId = thirdPartyId; }
    public String getTntRoles() { return tntRoles; }
    public void setTntRoles(String tntRoles) { this.tntRoles = tntRoles; }
    public String getKycStatus() { return kycStatus; }
    public void setKycStatus(String kycStatus) { this.kycStatus = kycStatus; }
    public String getPhoneAlias() { return phoneAlias; }
    public void setPhoneAlias(String phoneAlias) { this.phoneAlias = phoneAlias; }
    public boolean isPhoneMasked() { return phoneMasked; }
    public void setPhoneMasked(boolean phoneMasked) { this.phoneMasked = phoneMasked; }
    public Double getAverageRating() { return averageRating; }
    public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }
    public int getRatingCount() { return ratingCount; }
    public void setRatingCount(int ratingCount) { this.ratingCount = ratingCount; }
    public int getTotalDeliveries() { return totalDeliveries; }
    public void setTotalDeliveries(int totalDeliveries) { this.totalDeliveries = totalDeliveries; }
    public String getPreferredLocale() { return preferredLocale; }
    public void setPreferredLocale(String preferredLocale) { this.preferredLocale = preferredLocale; }
    public String getPreferredCurrency() { return preferredCurrency; }
    public void setPreferredCurrency(String preferredCurrency) { this.preferredCurrency = preferredCurrency; }
    public String getLoyaltyTier() { return loyaltyTier; }
    public void setLoyaltyTier(String loyaltyTier) { this.loyaltyTier = loyaltyTier; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getProviderLinksJson() { return providerLinksJson; }
    public void setProviderLinksJson(String v) { this.providerLinksJson = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
