package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;

/**
 * Pricing catalog entry – price stored in XAF.
 */
@Table("pricing_catalog")
public class PricingCatalog {
    @Id
    private UUID id;
    private String subscriptionType; // e.g. FREE, BASIC, STANDARD, PREMIUM, ADVANCE
    private java.math.BigDecimal priceXaf;

    public PricingCatalog() {}

    public PricingCatalog(UUID id, String subscriptionType, java.math.BigDecimal priceXaf) {
        this.id = id;
        this.subscriptionType = subscriptionType;
        this.priceXaf = priceXaf;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSubscriptionType() { return subscriptionType; }
    public void setSubscriptionType(String subscriptionType) { this.subscriptionType = subscriptionType; }

    public java.math.BigDecimal getPriceXaf() { return priceXaf; }
    public void setPriceXaf(java.math.BigDecimal priceXaf) { this.priceXaf = priceXaf; }
}
