package com.yowyob.tiibntick.core.product.domain.model;

import com.yowyob.tiibntick.common.vo.Money;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity: ProductVariant.
 *
 * <p>Represents a specific variant of a {@link Product} (e.g., size, colour, weight class).
 * A product must be ACTIVE before variants can be published.
 *
 * <p>Uses {@link Money} from {@code tnt-common-core} for full ISO 4217 support
 * including XAF (Central African CFA franc, 0 decimals).
 *
 * @author MANFOUO Braun
 */
public final class ProductVariant {

    private final UUID id;
    private final UUID productId;
    private final String sku;
    private final String name;
    private final Money price;
    private final Double weightKg;
    private final Map<String, String> attributes;
    private final int stockQuantity;
    private final boolean active;
    private final Instant createdAt;

    private ProductVariant(UUID id, UUID productId, String sku, String name, Money price,
                           Double weightKg, Map<String, String> attributes, int stockQuantity,
                           boolean active, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.productId = Objects.requireNonNull(productId, "productId must not be null");
        if (sku == null || sku.isBlank()) throw new IllegalArgumentException("sku must not be blank");
        this.sku = sku.strip().toUpperCase();
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        this.name = name.strip();
        this.price = Objects.requireNonNull(price, "price must not be null");
        this.weightKg = weightKg;
        this.attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
        if (stockQuantity < 0) throw new IllegalArgumentException("stockQuantity must be >= 0");
        this.stockQuantity = stockQuantity;
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static ProductVariant create(UUID productId, String sku, String name, Money price,
                                        Double weightKg, Map<String, String> attributes) {
        return new ProductVariant(UUID.randomUUID(), productId, sku, name, price,
                weightKg, attributes, 0, true, Instant.now());
    }

    public static ProductVariant rehydrate(UUID id, UUID productId, String sku, String name, Money price,
                                           Double weightKg, Map<String, String> attributes, int stockQuantity,
                                           boolean active, Instant createdAt) {
        return new ProductVariant(id, productId, sku, name, price, weightKg, attributes,
                stockQuantity, active, createdAt);
    }

    public ProductVariant withStock(int quantity) {
        return new ProductVariant(id, productId, sku, name, price, weightKg, attributes,
                quantity, active, createdAt);
    }

    public UUID id()                        { return id; }
    public UUID productId()                 { return productId; }
    public String sku()                     { return sku; }
    public String name()                    { return name; }
    public Money price()                    { return price; }
    public Double weightKg()               { return weightKg; }
    public Map<String, String> attributes() { return attributes; }
    public int stockQuantity()              { return stockQuantity; }
    public boolean isActive()               { return active; }
    public Instant createdAt()              { return createdAt; }
}
