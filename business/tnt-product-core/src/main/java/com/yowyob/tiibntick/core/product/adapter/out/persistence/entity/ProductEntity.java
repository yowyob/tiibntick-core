package com.yowyob.tiibntick.core.product.adapter.out.persistence.entity;

import com.yowyob.tiibntick.common.vo.Money;
import com.yowyob.tiibntick.core.product.domain.model.Dimensions;
import com.yowyob.tiibntick.core.product.domain.model.LogisticsProfile;
import com.yowyob.tiibntick.core.product.domain.model.Product;
import com.yowyob.tiibntick.core.product.domain.model.ProductStatus;
import com.yowyob.tiibntick.core.product.domain.model.ProductType;
import com.yowyob.tiibntick.core.product.domain.model.UnitOfMeasure;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * R2DBC persistence entity for {@link Product}.
 *
 * <p>Uses {@link Money} from {@code tnt-common-core} for all monetary fields.
 * The {@code catalog_product_id} column is the logical integration key to
 * yow_kernel_db.products.id (RT-comops-product-core). No physical FK cross-database.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_products")
public class ProductEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;
    private UUID tenantId;

    /**
     * Integration key → yow_kernel_db.products.id (RT-comops-product-core).
     * Null when this TNT product has no Kernel catalog counterpart.
     * No physical FK constraint — cross-database reference only.
     */
    @Column("catalog_product_id")
    private UUID catalogProductId;

    private String sku;
    private String name;
    private String description;
    private UUID categoryId;
    private String type;
    private BigDecimal basePriceAmount;
    private String basePriceCurrency;
    private String unit;
    private Double weightKg;
    private Double dimensionsLengthCm;
    private Double dimensionsWidthCm;
    private Double dimensionsHeightCm;
    private boolean requiresRefrigeration;
    private boolean requiresFragileHandling;
    private boolean isPerishable;
    private boolean isSensitive;
    private String packagingType;
    private String hazmatClass;
    private String specialInstructions;
    private String status;
    private String tags;
    private String attributesJson;
    private Instant createdAt;
    private Instant updatedAt;

    public static ProductEntity fromDomain(Product product) {
        ProductEntity entity = new ProductEntity();
        entity.id = product.id();
        entity.tenantId = product.tenantId();
        entity.catalogProductId = product.catalogProductId(); // nullable Kernel integration key
        entity.sku = product.sku();
        entity.name = product.name();
        entity.description = product.description();
        entity.categoryId = product.categoryId();
        entity.type = product.type().name();
        // tnt-common-core Money — use getAmount() / getCurrencyCode() (not record-style accessors)
        entity.basePriceAmount = product.basePrice().getAmount();
        entity.basePriceCurrency = product.basePrice().getCurrencyCode();
        entity.unit = product.unit().name();
        entity.weightKg = product.weightKg();
        if (product.dimensions() != null) {
            entity.dimensionsLengthCm = product.dimensions().lengthCm();
            entity.dimensionsWidthCm = product.dimensions().widthCm();
            entity.dimensionsHeightCm = product.dimensions().heightCm();
        }
        LogisticsProfile lp = product.logisticsProfile();
        entity.requiresRefrigeration = lp.requiresRefrigeration();
        entity.requiresFragileHandling = lp.requiresFragileHandling();
        entity.isPerishable = lp.isPerishable();
        entity.isSensitive = lp.isSensitive();
        entity.packagingType = lp.packagingType();
        entity.hazmatClass = lp.hazmatClass();
        entity.specialInstructions = lp.specialInstructions();
        entity.status = product.status().name();
        entity.tags = String.join(",", product.tags());
        entity.attributesJson = mapToJson(product.attributes());
        entity.createdAt = product.createdAt();
        entity.updatedAt = product.updatedAt();
        return entity;
    }

    public Product toDomain() {
        Dimensions dims = (dimensionsLengthCm != null)
                ? new Dimensions(dimensionsLengthCm, dimensionsWidthCm, dimensionsHeightCm)
                : null;
        LogisticsProfile lp = new LogisticsProfile(
                requiresRefrigeration, requiresFragileHandling,
                isPerishable, isSensitive, null, packagingType, hazmatClass, specialInstructions);
        // tnt-common-core Money — of(BigDecimal, String) matches both APIs
        Money price = Money.of(basePriceAmount, basePriceCurrency);
        List<String> tagList = (tags != null && !tags.isEmpty())
                ? List.of(tags.split(",")) : List.of();

        return Product.rehydrate(
                id, tenantId, catalogProductId, // pass catalogProductId through
                sku, name, description, categoryId,
                ProductType.valueOf(type), price, UnitOfMeasure.valueOf(unit), weightKg, dims, lp,
                ProductStatus.valueOf(status), List.of(), List.of(), tagList, Map.of(),
                createdAt, updatedAt);
    }

    private static String mapToJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> sb.append("\"").append(k).append("\":\"").append(v).append("\","));
        if (sb.length() > 1) sb.setLength(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }

    // ── Getters & Setters (required by R2DBC) ───────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getCatalogProductId() { return catalogProductId; }
    public void setCatalogProductId(UUID catalogProductId) { this.catalogProductId = catalogProductId; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getBasePriceAmount() { return basePriceAmount; }
    public void setBasePriceAmount(BigDecimal basePriceAmount) { this.basePriceAmount = basePriceAmount; }
    public String getBasePriceCurrency() { return basePriceCurrency; }
    public void setBasePriceCurrency(String basePriceCurrency) { this.basePriceCurrency = basePriceCurrency; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Double getWeightKg() { return weightKg; }
    public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }
    public Double getDimensionsLengthCm() { return dimensionsLengthCm; }
    public void setDimensionsLengthCm(Double d) { this.dimensionsLengthCm = d; }
    public Double getDimensionsWidthCm() { return dimensionsWidthCm; }
    public void setDimensionsWidthCm(Double d) { this.dimensionsWidthCm = d; }
    public Double getDimensionsHeightCm() { return dimensionsHeightCm; }
    public void setDimensionsHeightCm(Double d) { this.dimensionsHeightCm = d; }
    public boolean isRequiresRefrigeration() { return requiresRefrigeration; }
    public void setRequiresRefrigeration(boolean b) { this.requiresRefrigeration = b; }
    public boolean isRequiresFragileHandling() { return requiresFragileHandling; }
    public void setRequiresFragileHandling(boolean b) { this.requiresFragileHandling = b; }
    public boolean isPerishable() { return isPerishable; }
    public void setPerishable(boolean b) { isPerishable = b; }
    public boolean isSensitive() { return isSensitive; }
    public void setSensitive(boolean b) { isSensitive = b; }
    public String getPackagingType() { return packagingType; }
    public void setPackagingType(String s) { this.packagingType = s; }
    public String getHazmatClass() { return hazmatClass; }
    public void setHazmatClass(String s) { this.hazmatClass = s; }
    public String getSpecialInstructions() { return specialInstructions; }
    public void setSpecialInstructions(String s) { this.specialInstructions = s; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getAttributesJson() { return attributesJson; }
    public void setAttributesJson(String json) { this.attributesJson = json; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
