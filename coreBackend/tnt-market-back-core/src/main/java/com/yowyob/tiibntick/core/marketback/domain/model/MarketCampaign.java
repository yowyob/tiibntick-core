package com.yowyob.tiibntick.core.marketback.domain.model;

import com.yowyob.tiibntick.core.marketback.domain.exception.MarketDomainException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Aggregate Root — MarketCampaign (Promotional campaign on Market).
 * @author MANFOUO Braun
 */
public class MarketCampaign {

    private final CampaignId id;
    private final String tenantId;
    private final UUID adminId;
    private String name;
    private String description;
    private CampaignType type;
    private CampaignScope targetScope;
    private DiscountRule discount;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private CampaignStatus status;
    private int usageCount;
    private int maxUsage;
    private String promoCode;

    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MarketCampaign create(String tenantId, UUID adminId, String name,
            CampaignType type, DiscountRule discount, CampaignScope scope,
            LocalDateTime startDate, LocalDateTime endDate, int maxUsage, String promoCode) {
        return new MarketCampaign(tenantId, adminId, name, type, discount, scope, startDate, endDate, maxUsage, promoCode);
    }

    private MarketCampaign(String tenantId, UUID adminId, String name, CampaignType type,
            DiscountRule discount, CampaignScope scope, LocalDateTime startDate,
            LocalDateTime endDate, int maxUsage, String promoCode) {
        this.id = CampaignId.generate();
        this.tenantId = tenantId;
        this.adminId = adminId;
        this.name = name;
        this.type = type;
        this.discount = discount;
        this.targetScope = scope;
        this.startDate = startDate;
        this.endDate = endDate;
        this.maxUsage = maxUsage;
        this.promoCode = promoCode;
        this.status = CampaignStatus.DRAFT;
        this.usageCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    private Money budget;
    private Money spentAmount;

    MarketCampaign() {
        this.id = null; this.tenantId = null; this.adminId = null; this.createdAt = null;
    }

    /** Reconstitutes a MarketCampaign from its persisted state. */
    public static MarketCampaign reconstitute(
            CampaignId id, String tenantId, String name, String description,
            CampaignType campaignType, CampaignStatus status,
            DiscountRule discountRule, CampaignScope scope,
            Money budget, Money spentAmount,
            LocalDateTime startDate, LocalDateTime endDate,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new MarketCampaign(id, tenantId, name, description, campaignType, status,
                discountRule, scope, budget, spentAmount, startDate, endDate, createdAt, updatedAt);
    }

    private MarketCampaign(
            CampaignId id, String tenantId, String name, String description,
            CampaignType campaignType, CampaignStatus status,
            DiscountRule discountRule, CampaignScope scope,
            Money budget, Money spentAmount,
            LocalDateTime startDate, LocalDateTime endDate,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.adminId = null;
        this.name = name;
        this.description = description;
        this.type = campaignType;
        this.status = status;
        this.discount = discountRule;
        this.targetScope = scope;
        this.budget = budget;
        this.spentAmount = spentAmount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void activate() {
        if (status == CampaignStatus.TERMINATED || status == CampaignStatus.EXPIRED) {
            throw new MarketDomainException("Cannot activate a terminated or expired campaign.");
        }
        this.status = CampaignStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public void pause() {
        this.status = CampaignStatus.PAUSED;
        this.updatedAt = LocalDateTime.now();
    }

    public void terminate() {
        this.status = CampaignStatus.TERMINATED;
        this.updatedAt = LocalDateTime.now();
    }

    public void recordUsage() {
        this.usageCount++;
        if (maxUsage > 0 && usageCount >= maxUsage) {
            this.status = CampaignStatus.EXPIRED;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActiveAt(LocalDateTime moment) {
        return status == CampaignStatus.ACTIVE
                && (startDate == null || !moment.isBefore(startDate))
                && (endDate == null || !moment.isAfter(endDate));
    }

    public boolean canBeUsed() {
        return isActiveAt(LocalDateTime.now())
                && (maxUsage <= 0 || usageCount < maxUsage);
    }

    public boolean applicableToListing(MarketListingId listingId) {
        return targetScope != null && targetScope.includes(listingId);
    }

    public Money computeDiscount(Money orderAmount) {
        return discount.apply(orderAmount);
    }

    public CampaignId getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getAdminId() { return adminId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public CampaignType getType() { return type; }
    public CampaignType getCampaignType() { return type; }   // alias
    public CampaignScope getTargetScope() { return targetScope; }
    public CampaignScope getScope() { return targetScope; }  // alias
    public DiscountRule getDiscount() { return discount; }
    public DiscountRule getDiscountRule() { return discount; } // alias
    public Money getBudget() { return budget; }
    public Money getSpentAmount() { return spentAmount; }
    public LocalDateTime getStartDate() { return startDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public CampaignStatus getStatus() { return status; }
    public int getUsageCount() { return usageCount; }
    public int getMaxUsage() { return maxUsage; }
    public String getPromoCode() { return promoCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
