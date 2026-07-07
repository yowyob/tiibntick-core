package com.yowyob.tiibntick.core.product.adapter.out.persistence.entity;

import com.yowyob.tiibntick.core.product.domain.model.ProductStatus;
import com.yowyob.tiibntick.core.product.domain.model.ServiceOffer;
import com.yowyob.tiibntick.core.product.domain.model.ServiceType;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for {@link ServiceOffer}.
 *
 * <p>The {@code catalog_product_id} column is the logical integration key to
 * yow_kernel_db.products.id (RT-comops-product-core). Null for general-purpose offers.
 * No physical FK constraint — cross-database reference only.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_service_offers")
public class ServiceOfferEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;
    private UUID tenantId;
    private UUID providerId;

    /**
     * Integration key → yow_kernel_db.products.id (RT-comops-product-core).
     * Null for general-purpose offers not tied to a specific Kernel product type.
     * No physical FK constraint — cross-database reference only.
     */
    @Column("catalog_product_id")
    private UUID catalogProductId;

    private String name;
    private String description;
    private String type;
    private double maxWeightKg;
    private Double maxDistanceKm;
    private int deliveryWindowHours;
    private UUID coverageZoneId;
    private String policyId;
    private boolean publishedOnMarket;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public static ServiceOfferEntity fromDomain(ServiceOffer offer) {
        ServiceOfferEntity e = new ServiceOfferEntity();
        e.id = offer.id();
        e.tenantId = offer.tenantId();
        e.providerId = offer.providerId();
        e.catalogProductId = offer.catalogProductId(); // nullable Kernel integration key
        e.name = offer.name();
        e.description = offer.description();
        e.type = offer.type().name();
        e.maxWeightKg = offer.maxWeightKg();
        e.maxDistanceKm = offer.maxDistanceKm();
        e.deliveryWindowHours = offer.deliveryWindowHours();
        e.coverageZoneId = offer.coverageZoneId();
        e.policyId = offer.policyId();
        e.publishedOnMarket = offer.isPublishedOnMarket();
        e.status = offer.status().name();
        e.createdAt = offer.createdAt();
        e.updatedAt = offer.updatedAt();
        return e;
    }

    public ServiceOffer toDomain() {
        return ServiceOffer.rehydrate(
                id, tenantId, providerId, catalogProductId, // pass catalogProductId through
                name, description, ServiceType.valueOf(type),
                maxWeightKg, maxDistanceKm, deliveryWindowHours,
                coverageZoneId, policyId, publishedOnMarket,
                ProductStatus.valueOf(status), createdAt, updatedAt);
    }

    // ── Getters & Setters (required by R2DBC) ───────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID t) { this.tenantId = t; }
    public UUID getProviderId() { return providerId; }
    public void setProviderId(UUID p) { this.providerId = p; }
    public UUID getCatalogProductId() { return catalogProductId; }
    public void setCatalogProductId(UUID c) { this.catalogProductId = c; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public double getMaxWeightKg() { return maxWeightKg; }
    public void setMaxWeightKg(double m) { this.maxWeightKg = m; }
    public Double getMaxDistanceKm() { return maxDistanceKm; }
    public void setMaxDistanceKm(Double m) { this.maxDistanceKm = m; }
    public int getDeliveryWindowHours() { return deliveryWindowHours; }
    public void setDeliveryWindowHours(int d) { this.deliveryWindowHours = d; }
    public UUID getCoverageZoneId() { return coverageZoneId; }
    public void setCoverageZoneId(UUID c) { this.coverageZoneId = c; }
    public String getPolicyId() { return policyId; }
    public void setPolicyId(String p) { this.policyId = p; }
    public boolean isPublishedOnMarket() { return publishedOnMarket; }
    public void setPublishedOnMarket(boolean b) { this.publishedOnMarket = b; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant c) { this.createdAt = c; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant u) { this.updatedAt = u; }
}
