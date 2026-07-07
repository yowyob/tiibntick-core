package com.yowyob.tiibntick.core.product.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ProductCategory {

    private final UUID id;
    private final UUID tenantId;
    private final String name;
    private final UUID parentCategoryId;
    private final String description;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ProductCategory(UUID id, UUID tenantId, String name, UUID parentCategoryId,
                            String description, boolean active, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        this.name = name.strip();
        this.parentCategoryId = parentCategoryId;
        this.description = description;
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static ProductCategory create(UUID tenantId, String name, UUID parentCategoryId, String description) {
        Instant now = Instant.now();
        return new ProductCategory(UUID.randomUUID(), tenantId, name, parentCategoryId, description, true, now, now);
    }

    public static ProductCategory rehydrate(UUID id, UUID tenantId, String name, UUID parentCategoryId,
                                            String description, boolean active, Instant createdAt, Instant updatedAt) {
        return new ProductCategory(id, tenantId, name, parentCategoryId, description, active, createdAt, updatedAt);
    }

    public ProductCategory deactivate() {
        return new ProductCategory(id, tenantId, name, parentCategoryId, description, false, createdAt, Instant.now());
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String name() { return name; }
    public UUID parentCategoryId() { return parentCategoryId; }
    public String description() { return description; }
    public boolean isActive() { return active; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
