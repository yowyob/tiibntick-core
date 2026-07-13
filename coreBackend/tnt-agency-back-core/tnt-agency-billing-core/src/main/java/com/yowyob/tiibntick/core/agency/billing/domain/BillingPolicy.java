package com.yowyob.tiibntick.core.agency.billing.domain;

import com.yowyob.tiibntick.common.domain.model.TntBaseEntity;
import com.yowyob.tiibntick.core.agency.billing.domain.vo.PolicyStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class BillingPolicy extends TntBaseEntity {

    private final UUID agencyId;
    private String name;
    private String description;
    private PolicyStatus status;
    private final String currency;
    private BigDecimal basePrice;
    private BigDecimal pricePerKm;
    private BigDecimal pricePerKg;
    private BigDecimal minPrice;
    private UUID corePolicyId;

    public BillingPolicy(UUID id, UUID tenantId, UUID agencyId, String name, String description,
                         PolicyStatus status, String currency, BigDecimal basePrice,
                         BigDecimal pricePerKm, BigDecimal pricePerKg, BigDecimal minPrice,
                         UUID corePolicyId, Instant createdAt, Instant updatedAt, long version) {
        super(id, tenantId, createdAt, updatedAt, version);
        this.agencyId = agencyId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.currency = currency;
        this.basePrice = basePrice;
        this.pricePerKm = pricePerKm;
        this.pricePerKg = pricePerKg;
        this.minPrice = minPrice;
        this.corePolicyId = corePolicyId;
    }

    public static BillingPolicy create(UUID id, UUID tenantId, UUID agencyId, String name,
                                       String description, String currency, BigDecimal basePrice,
                                       BigDecimal pricePerKm, BigDecimal pricePerKg,
                                       BigDecimal minPrice, Instant now) {
        return new BillingPolicy(
                id, tenantId, agencyId, name, description, PolicyStatus.DRAFT,
                currency, basePrice, pricePerKm, pricePerKg, minPrice, null, now, now, 0L
        );
    }

    public void activate(Instant now) {
        if (status == PolicyStatus.ARCHIVED) {
            throw new IllegalStateException("An archived policy cannot be reactivated");
        }
        this.status = PolicyStatus.ACTIVE;
        markUpdated(now);
    }

    public void archive(Instant now) {
        this.status = PolicyStatus.ARCHIVED;
        markUpdated(now);
    }

    public BigDecimal estimate(double distanceKm, double weightKg) {
        BigDecimal estimated = basePrice
                .add(pricePerKm.multiply(BigDecimal.valueOf(distanceKm)))
                .add(pricePerKg.multiply(BigDecimal.valueOf(weightKg)));
        return estimated.compareTo(minPrice) < 0 ? minPrice : estimated;
    }

    public void linkCorePolicy(UUID corePolicyId, Instant now) {
        this.corePolicyId = corePolicyId;
        markUpdated(now);
    }

    public UUID getAgencyId() { return agencyId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public PolicyStatus getStatus() { return status; }
    public String getCurrency() { return currency; }
    public BigDecimal getBasePrice() { return basePrice; }
    public BigDecimal getPricePerKm() { return pricePerKm; }
    public BigDecimal getPricePerKg() { return pricePerKg; }
    public BigDecimal getMinPrice() { return minPrice; }
    public UUID getCorePolicyId() { return corePolicyId; }
}
