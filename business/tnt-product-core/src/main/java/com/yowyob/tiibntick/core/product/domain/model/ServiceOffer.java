package com.yowyob.tiibntick.core.product.domain.model;

import com.yowyob.tiibntick.core.product.domain.exception.ServiceOfferStatusTransitionException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root: ServiceOffer.
 *
 * <p>Represents a logistics service offer published by an agency or freelancer provider.
 * Defines the conditions under which the provider will accept a delivery mission:
 * max weight, max distance, delivery window, coverage zone and pricing policy.
 *
 * <p><strong>Kernel integration:</strong> {@code catalogProductId} is a nullable UUID reference
 * to the Yowyob Kernel product catalog entry (RT-comops-product-core, yow_kernel_db) describing
 * the base product type this offer handles. Null for general-purpose offers that accept any
 * product type within their weight/distance constraints.
 * Logical reference only — no physical FK cross-database.
 *
 * @author MANFOUO Braun
 */
public final class ServiceOffer {

    private final UUID id;
    private final UUID tenantId;
    private final UUID providerId;

    /**
     * Integration key → yow_kernel_db.products.id (RT-comops-product-core).
     * Null for general-purpose offers not tied to a specific Kernel product type.
     * Logical reference only — no physical FK cross-database.
     */
    private final UUID catalogProductId;

    private final String name;
    private final String description;
    private final ServiceType type;
    private final double maxWeightKg;
    private final Double maxDistanceKm;
    private final int deliveryWindowHours;
    private final UUID coverageZoneId;
    private final String policyId;
    private final boolean publishedOnMarket;
    private final ProductStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ServiceOffer(UUID id, UUID tenantId, UUID providerId, UUID catalogProductId,
                         String name, String description, ServiceType type,
                         double maxWeightKg, Double maxDistanceKm, int deliveryWindowHours,
                         UUID coverageZoneId, String policyId,
                         boolean publishedOnMarket, ProductStatus status,
                         Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.providerId = Objects.requireNonNull(providerId, "providerId must not be null");
        this.catalogProductId = catalogProductId; // nullable — Kernel integration key
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        this.name = name.strip();
        this.description = description;
        this.type = Objects.requireNonNull(type, "type must not be null");
        if (maxWeightKg <= 0) throw new IllegalArgumentException("maxWeightKg must be positive");
        this.maxWeightKg = maxWeightKg;
        this.maxDistanceKm = maxDistanceKm;
        if (deliveryWindowHours <= 0)
            throw new IllegalArgumentException("deliveryWindowHours must be positive");
        this.deliveryWindowHours = deliveryWindowHours;
        this.coverageZoneId = coverageZoneId;
        this.policyId = policyId;
        this.publishedOnMarket = publishedOnMarket;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * Creates a new ServiceOffer in DRAFT status with an optional Kernel catalog product reference.
     *
     * @param catalogProductId optional UUID of the Kernel catalog product this offer handles — may be null
     */
    public static ServiceOffer create(UUID tenantId, UUID providerId, UUID catalogProductId,
                                      String name, String description, ServiceType type,
                                      double maxWeightKg, Double maxDistanceKm, int deliveryWindowHours,
                                      UUID coverageZoneId, String policyId) {
        Instant now = Instant.now();
        return new ServiceOffer(UUID.randomUUID(), tenantId, providerId, catalogProductId,
                name, description, type, maxWeightKg, maxDistanceKm, deliveryWindowHours,
                coverageZoneId, policyId, false, ProductStatus.DRAFT, now, now);
    }

    /**
     * Backward-compatible factory — no catalogProductId (defaults to null).
     */
    public static ServiceOffer create(UUID tenantId, UUID providerId,
                                      String name, String description, ServiceType type,
                                      double maxWeightKg, Double maxDistanceKm, int deliveryWindowHours,
                                      UUID coverageZoneId, String policyId) {
        return create(tenantId, providerId, null, name, description, type,
                maxWeightKg, maxDistanceKm, deliveryWindowHours, coverageZoneId, policyId);
    }

    /** Rehydrates a ServiceOffer from persistent storage — includes all fields. */
    public static ServiceOffer rehydrate(UUID id, UUID tenantId, UUID providerId, UUID catalogProductId,
                                         String name, String description, ServiceType type,
                                         double maxWeightKg, Double maxDistanceKm, int deliveryWindowHours,
                                         UUID coverageZoneId, String policyId,
                                         boolean publishedOnMarket, ProductStatus status,
                                         Instant createdAt, Instant updatedAt) {
        return new ServiceOffer(id, tenantId, providerId, catalogProductId, name, description,
                type, maxWeightKg, maxDistanceKm, deliveryWindowHours, coverageZoneId, policyId,
                publishedOnMarket, status, createdAt, updatedAt);
    }

    /**
     * Backward-compatible rehydrate — without catalogProductId (defaults to null).
     */
    public static ServiceOffer rehydrate(UUID id, UUID tenantId, UUID providerId,
                                         String name, String description, ServiceType type,
                                         double maxWeightKg, Double maxDistanceKm, int deliveryWindowHours,
                                         UUID coverageZoneId, String policyId,
                                         boolean publishedOnMarket, ProductStatus status,
                                         Instant createdAt, Instant updatedAt) {
        return rehydrate(id, tenantId, providerId, null, name, description, type,
                maxWeightKg, maxDistanceKm, deliveryWindowHours, coverageZoneId, policyId,
                publishedOnMarket, status, createdAt, updatedAt);
    }

    // ── State machine ────────────────────────────────────────────────────────────

    /** Activates the offer so it can be published on the market. */
    public ServiceOffer activate() {
        if (status == ProductStatus.ARCHIVED) {
            throw new ServiceOfferStatusTransitionException(id, status, ProductStatus.ACTIVE);
        }
        return withStatus(ProductStatus.ACTIVE, publishedOnMarket);
    }

    /**
     * Publishes the offer on TiiBnTick Market for discovery by clients.
     * The offer must be ACTIVE to be published.
     */
    public ServiceOffer publishToMarket() {
        if (status != ProductStatus.ACTIVE) {
            throw new ServiceOfferStatusTransitionException(id, status, ProductStatus.ACTIVE,
                    "Offer must be ACTIVE before publishing to market");
        }
        return withStatus(ProductStatus.ACTIVE, true);
    }

    /** Removes the offer from public market visibility (does not deactivate it). */
    public ServiceOffer unpublishFromMarket() {
        return withStatus(status, false);
    }

    /** Permanently archives the offer. Cannot be reversed to ACTIVE. */
    public ServiceOffer archive() {
        return withStatus(ProductStatus.ARCHIVED, false);
    }

    /**
     * Checks whether this offer can handle a mission with the given parameters.
     *
     * @param weightKg   package weight in kg
     * @param distanceKm delivery distance in km
     * @return true if offer can handle the mission
     */
    public boolean matchesMission(double weightKg, double distanceKm) {
        if (status != ProductStatus.ACTIVE) return false;
        if (weightKg > maxWeightKg) return false;
        if (maxDistanceKm != null && distanceKm > maxDistanceKm) return false;
        return true;
    }

    private ServiceOffer withStatus(ProductStatus newStatus, boolean published) {
        return new ServiceOffer(id, tenantId, providerId, catalogProductId, name, description,
                type, maxWeightKg, maxDistanceKm, deliveryWindowHours, coverageZoneId, policyId,
                published, newStatus, createdAt, Instant.now());
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public UUID id()               { return id; }
    public UUID tenantId()         { return tenantId; }
    public UUID providerId()       { return providerId; }
    /** Kernel integration key — may be null. */
    public UUID catalogProductId() { return catalogProductId; }
    public String name()           { return name; }
    public String description()    { return description; }
    public ServiceType type()      { return type; }
    public double maxWeightKg()    { return maxWeightKg; }
    public Double maxDistanceKm()  { return maxDistanceKm; }
    public int deliveryWindowHours() { return deliveryWindowHours; }
    public UUID coverageZoneId()   { return coverageZoneId; }
    public String policyId()       { return policyId; }
    public boolean isPublishedOnMarket() { return publishedOnMarket; }
    public ProductStatus status()  { return status; }
    public Instant createdAt()     { return createdAt; }
    public Instant updatedAt()     { return updatedAt; }
}
