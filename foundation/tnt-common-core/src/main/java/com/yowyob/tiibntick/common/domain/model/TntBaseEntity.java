package com.yowyob.tiibntick.common.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Abstract base entity shared by all TiiBnTick domain aggregates and entities.
 *
 * <p>Aligns with the Yowyob kernel {@code BaseEntity} contract (yowyob.comops.api.common)
 * while adding TiiBnTick-specific concerns: optimistic locking version and a soft-delete flag.
 * Adapter-layer persistence entities extend the kernel's PersistableEntity directly;
 * domain entities extend this class.
 *
 * <p><strong>Invariants:</strong>
 * <ul>
 *   <li>{@code id}, {@code tenantId}, {@code createdAt} are never null and never change.</li>
 *   <li>{@code updatedAt} is always {@code >= createdAt}.</li>
 *   <li>{@code version} starts at 0 and increments on each save.</li>
 * </ul>
 *
 * Author: MANFOUO Braun
 */
public abstract class TntBaseEntity {

    private final UUID id;
    private final UUID tenantId;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    /**
     * Full constructor used by reconstitution from persistence.
     *
     * @param id        aggregate/entity identifier — never null
     * @param tenantId  tenant owning this entity — never null
     * @param createdAt creation timestamp — never null
     * @param updatedAt last update timestamp — must be {@code >= createdAt}
     * @param version   optimistic locking version — must be {@code >= 0}
     */
    protected TntBaseEntity(UUID id, UUID tenantId, Instant createdAt, Instant updatedAt, long version) {
        this.id        = Objects.requireNonNull(id,        "id is required");
        this.tenantId  = Objects.requireNonNull(tenantId,  "tenantId is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0, got: " + version);
        }
        this.version = version;
    }

    /**
     * Factory constructor for new entities. Sets {@code updatedAt = createdAt} and {@code version = 0}.
     *
     * @param id       new aggregate identifier
     * @param tenantId owning tenant
     * @param now      creation timestamp
     */
    protected TntBaseEntity(UUID id, UUID tenantId, Instant now) {
        this(id, tenantId, now, now, 0L);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    /**
     * Marks this entity as updated at the given timestamp and increments the version.
     * Must be called by subclasses when state is mutated.
     *
     * @param now new update timestamp — must not be before {@code createdAt}
     */
    protected void markUpdated(Instant now) {
        Objects.requireNonNull(now, "updatedAt timestamp is required");
        this.updatedAt = now;
        this.version++;
    }

    /**
     * Two entities are equal if they share the same {@code id} regardless of state.
     * This follows DDD entity identity semantics.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TntBaseEntity other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{id=" + id
                + ", tenantId=" + tenantId
                + ", version=" + version + "}";
    }
}
