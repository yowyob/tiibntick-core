package com.yowyob.tiibntick.core.product.domain.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.yowyob.tiibntick.common.vo.Money;
import com.yowyob.tiibntick.core.product.domain.exception.ProductStatusTransitionException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root: Product.
 *
 * <p>Represents a TiiBnTick logistics product in the tenant's catalog. Manages the full
 * lifecycle: DRAFT → ACTIVE → ARCHIVED (or OUT_OF_STOCK from ACTIVE).
 *
 * <p><strong>Kernel integration:</strong> {@code catalogProductId} is a nullable UUID reference
 * to the corresponding product in the Yowyob Kernel catalog (RT-comops-product-core,
 * yow_kernel_db). Null when the product has no Kernel counterpart.
 * Logical reference only — no physical FK cross-database.
 *
 * <p>{@link Money} is imported from {@code tnt-common-core} (ISO 4217, XAF/NGN/KES support).
 * The local duplicate Money class has been removed.
 *
 * @author MANFOUO Braun
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE)
public final class Product {

    private final UUID id;
    private final UUID tenantId;

    /**
     * Integration key → yow_kernel_db.products.id (RT-comops-product-core).
     * Null when this TNT product has no equivalent in the Kernel catalog.
     * Logical reference only — no physical FK cross-database.
     */
    private final UUID catalogProductId;

    private final String sku;
    private final String name;
    private final String description;
    private final UUID categoryId;
    private final ProductType type;
    private final Money basePrice;
    private final UnitOfMeasure unit;
    private final Double weightKg;
    private final Dimensions dimensions;
    private final LogisticsProfile logisticsProfile;
    private final ProductStatus status;
    private final List<ProductVariant> variants;
    private final List<String> imageKeys;
    private final List<String> tags;
    private final Map<String, String> attributes;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Product(UUID id, UUID tenantId, UUID catalogProductId,
                    String sku, String name, String description,
                    UUID categoryId, ProductType type, Money basePrice, UnitOfMeasure unit,
                    Double weightKg, Dimensions dimensions, LogisticsProfile logisticsProfile,
                    ProductStatus status, List<ProductVariant> variants, List<String> imageKeys,
                    List<String> tags, Map<String, String> attributes,
                    Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.catalogProductId = catalogProductId; // nullable — Kernel integration key
        if (sku == null || sku.isBlank()) throw new IllegalArgumentException("sku must not be blank");
        this.sku = sku.strip().toUpperCase();
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        this.name = name.strip();
        this.description = description;
        this.categoryId = categoryId;
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.basePrice = Objects.requireNonNull(basePrice, "basePrice must not be null");
        this.unit = Objects.requireNonNull(unit, "unit must not be null");
        this.weightKg = weightKg;
        this.dimensions = dimensions;
        this.logisticsProfile = logisticsProfile != null ? logisticsProfile : LogisticsProfile.standard();
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.variants = variants != null ? List.copyOf(variants) : List.of();
        this.imageKeys = imageKeys != null ? List.copyOf(imageKeys) : List.of();
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        this.attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * Creates a new Product in DRAFT status.
     *
     * @param catalogProductId optional Kernel product UUID — may be null
     */
    public static Product create(UUID tenantId, UUID catalogProductId,
                                 String sku, String name, String description,
                                 UUID categoryId, ProductType type, Money basePrice, UnitOfMeasure unit,
                                 Double weightKg, Dimensions dimensions, LogisticsProfile logisticsProfile,
                                 List<String> tags, Map<String, String> attributes) {
        Instant now = Instant.now();
        return new Product(UUID.randomUUID(), tenantId, catalogProductId,
                sku, name, description, categoryId, type, basePrice, unit,
                weightKg, dimensions, logisticsProfile, ProductStatus.DRAFT,
                List.of(), List.of(), tags, attributes, now, now);
    }

    /**
     * Backward-compatible factory — no catalogProductId (defaults to null).
     */
    public static Product create(UUID tenantId, String sku, String name, String description,
                                 UUID categoryId, ProductType type, Money basePrice, UnitOfMeasure unit,
                                 Double weightKg, Dimensions dimensions, LogisticsProfile logisticsProfile,
                                 List<String> tags, Map<String, String> attributes) {
        return create(tenantId, null, sku, name, description, categoryId, type,
                basePrice, unit, weightKg, dimensions, logisticsProfile, tags, attributes);
    }

    /** Rehydrates a Product from persistent storage — includes all fields. */
    public static Product rehydrate(UUID id, UUID tenantId, UUID catalogProductId,
                                    String sku, String name, String description,
                                    UUID categoryId, ProductType type, Money basePrice, UnitOfMeasure unit,
                                    Double weightKg, Dimensions dimensions, LogisticsProfile logisticsProfile,
                                    ProductStatus status, List<ProductVariant> variants, List<String> imageKeys,
                                    List<String> tags, Map<String, String> attributes,
                                    Instant createdAt, Instant updatedAt) {
        return new Product(id, tenantId, catalogProductId, sku, name, description, categoryId,
                type, basePrice, unit, weightKg, dimensions, logisticsProfile, status,
                variants, imageKeys, tags, attributes, createdAt, updatedAt);
    }

    /**
     * Backward-compatible rehydrate — without catalogProductId (defaults to null).
     * Used by adapter mapping code that predates the Kernel integration.
     */
    public static Product rehydrate(UUID id, UUID tenantId,
                                    String sku, String name, String description,
                                    UUID categoryId, ProductType type, Money basePrice, UnitOfMeasure unit,
                                    Double weightKg, Dimensions dimensions, LogisticsProfile logisticsProfile,
                                    ProductStatus status, List<ProductVariant> variants, List<String> imageKeys,
                                    List<String> tags, Map<String, String> attributes,
                                    Instant createdAt, Instant updatedAt) {
        return rehydrate(id, tenantId, null, sku, name, description, categoryId, type,
                basePrice, unit, weightKg, dimensions, logisticsProfile, status,
                variants, imageKeys, tags, attributes, createdAt, updatedAt);
    }

    // ── State machine ────────────────────────────────────────────────────────────

    public Product activate() {
        if (status == ProductStatus.ARCHIVED) {
            throw new ProductStatusTransitionException(id, status, ProductStatus.ACTIVE);
        }
        return withStatus(ProductStatus.ACTIVE);
    }

    public Product archive() {
        if (status == ProductStatus.ARCHIVED) {
            throw new ProductStatusTransitionException(id, status, ProductStatus.ARCHIVED);
        }
        return withStatus(ProductStatus.ARCHIVED);
    }

    public Product markOutOfStock() {
        if (status != ProductStatus.ACTIVE) {
            throw new ProductStatusTransitionException(id, status, ProductStatus.OUT_OF_STOCK);
        }
        return withStatus(ProductStatus.OUT_OF_STOCK);
    }

    public Product updatePrice(Money newPrice) {
        Objects.requireNonNull(newPrice, "newPrice must not be null");
        return new Product(id, tenantId, catalogProductId, sku, name, description, categoryId,
                type, newPrice, unit, weightKg, dimensions, logisticsProfile, status,
                variants, imageKeys, tags, attributes, createdAt, Instant.now());
    }

    public Product addVariant(ProductVariant variant) {
        Objects.requireNonNull(variant, "variant must not be null");
        List<ProductVariant> updated = new ArrayList<>(variants);
        updated.add(variant);
        return new Product(id, tenantId, catalogProductId, sku, name, description, categoryId,
                type, basePrice, unit, weightKg, dimensions, logisticsProfile, status,
                updated, imageKeys, tags, attributes, createdAt, Instant.now());
    }

    public Product addImageKey(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) return this;
        List<String> updated = new ArrayList<>(imageKeys);
        updated.add(imageKey.strip());
        return new Product(id, tenantId, catalogProductId, sku, name, description, categoryId,
                type, basePrice, unit, weightKg, dimensions, logisticsProfile, status,
                variants, updated, tags, attributes, createdAt, Instant.now());
    }

    /** Links this product to a Kernel catalog product UUID (post-creation enrichment). */
    public Product withCatalogProductId(UUID kernelProductId) {
        return new Product(id, tenantId, kernelProductId, sku, name, description, categoryId,
                type, basePrice, unit, weightKg, dimensions, logisticsProfile, status,
                variants, imageKeys, tags, attributes, createdAt, Instant.now());
    }

    public boolean isAvailable() { return status == ProductStatus.ACTIVE; }
    public boolean requiresSpecialHandling() { return logisticsProfile.requiresSpecialHandling(); }

    private Product withStatus(ProductStatus newStatus) {
        return new Product(id, tenantId, catalogProductId, sku, name, description, categoryId,
                type, basePrice, unit, weightKg, dimensions, logisticsProfile, newStatus,
                variants, imageKeys, tags, attributes, createdAt, Instant.now());
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public UUID id()                      { return id; }
    public UUID tenantId()                { return tenantId; }
    /** Kernel integration key — may be null. */
    public UUID catalogProductId()        { return catalogProductId; }
    public String sku()                   { return sku; }
    public String name()                  { return name; }
    public String description()           { return description; }
    public UUID categoryId()              { return categoryId; }
    public ProductType type()             { return type; }
    public Money basePrice()              { return basePrice; }
    public UnitOfMeasure unit()           { return unit; }
    public Double weightKg()              { return weightKg; }
    public Dimensions dimensions()        { return dimensions; }
    public LogisticsProfile logisticsProfile() { return logisticsProfile; }
    public ProductStatus status()         { return status; }
    public List<ProductVariant> variants() { return variants; }
    public List<String> imageKeys()       { return imageKeys; }
    public List<String> tags()            { return tags; }
    public Map<String, String> attributes() { return attributes; }
    public Instant createdAt()            { return createdAt; }
    public Instant updatedAt()            { return updatedAt; }
}
