package com.yowyob.tiibntick.core.marketback.domain.model;

import com.yowyob.tiibntick.core.marketback.domain.exception.MarketDomainException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Aggregate Root — ServiceOffer.
 *
 * <p>A ServiceOffer is a specific logistics service configuration published
 * by a provider on their TiiBnTick Market listing. It defines pricing rules,
 * handling constraints and availability schedule.</p>
 *
 * @author MANFOUO Braun
 */
public class ServiceOffer {

    private final ServiceOfferId id;
    private final String tenantId;
    private final MarketListingId listingId;
    private final UUID providerId;

    private String name;
    private String description;
    private ServiceType serviceType;
    private OfferStatus status;
    private PricingRules pricingRules;
    private ServiceConstraints serviceConstraints;
    private OfferAvailability availability;

    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // -------------------------------------------------------
    // Factory
    // -------------------------------------------------------

    public static ServiceOffer create(
            String tenantId,
            MarketListingId listingId,
            UUID providerId,
            String name,
            ServiceType serviceType,
            PricingRules pricingRules,
            ServiceConstraints constraints,
            OfferAvailability availability) {
        ServiceOffer offer = new ServiceOffer();
        return new ServiceOffer(tenantId, listingId, providerId, name,
                serviceType, pricingRules, constraints, availability);
    }

    private ServiceOffer(
            String tenantId,
            MarketListingId listingId,
            UUID providerId,
            String name,
            ServiceType serviceType,
            PricingRules pricingRules,
            ServiceConstraints constraints,
            OfferAvailability availability) {
        this.id = ServiceOfferId.generate();
        this.tenantId = tenantId;
        this.listingId = listingId;
        this.providerId = providerId;
        this.name = name;
        this.serviceType = serviceType;
        this.pricingRules = pricingRules;
        this.serviceConstraints = constraints;
        this.availability = availability;
        this.status = OfferStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    // Reconstitution constructor
    ServiceOffer() {
        this.id = null;
        this.tenantId = null;
        this.listingId = null;
        this.providerId = null;
        this.createdAt = null;
    }

    /** Reconstitutes a ServiceOffer from its persisted state. */
    public static ServiceOffer reconstitute(
            ServiceOfferId id, String tenantId, MarketListingId listingId,
            UUID providerId, String name, String description,
            ServiceType serviceType, OfferStatus status,
            PricingRules pricingRules, ServiceConstraints constraints,
            OfferAvailability availability,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        ServiceOffer o = new ServiceOffer();
        return new ServiceOffer(id, tenantId, listingId, providerId, name, description,
                serviceType, status, pricingRules, constraints, availability, createdAt, updatedAt);
    }

    private ServiceOffer(
            ServiceOfferId id, String tenantId, MarketListingId listingId,
            UUID providerId, String name, String description,
            ServiceType serviceType, OfferStatus status,
            PricingRules pricingRules, ServiceConstraints constraints,
            OfferAvailability availability,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.listingId = listingId;
        this.providerId = providerId;
        this.name = name;
        this.description = description;
        this.serviceType = serviceType;
        this.status = status;
        this.pricingRules = pricingRules;
        this.serviceConstraints = constraints;
        this.availability = availability;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Activates the offer so it becomes visible to clients. */
    public void activate() {
        if (status == OfferStatus.ARCHIVED) {
            throw new MarketDomainException("Cannot activate an archived offer.");
        }
        this.status = OfferStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /** Temporarily deactivates the offer (e.g. provider unavailable). */
    public void deactivate() {
        if (status == OfferStatus.ARCHIVED) {
            throw new MarketDomainException("Cannot deactivate an archived offer.");
        }
        this.status = OfferStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /** Permanently archives the offer. */
    public void archive() {
        this.status = OfferStatus.ARCHIVED;
        this.updatedAt = LocalDateTime.now();
    }

    /** Updates pricing rules. */
    public void updatePricing(PricingRules newRules) {
        this.pricingRules = newRules;
        this.updatedAt = LocalDateTime.now();
    }

    /** Updates service handling constraints. */
    public void updateConstraints(ServiceConstraints newConstraints) {
        this.serviceConstraints = newConstraints;
        this.updatedAt = LocalDateTime.now();
    }

    /** Updates availability schedule. */
    public void updateAvailability(OfferAvailability newAvailability) {
        this.availability = newAvailability;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Returns true if this offer can fulfill the given delivery request.
     * Checks: service constraints and availability schedule.
     */
    public boolean isAvailableFor(DeliveryRequest request) {
        if (status != OfferStatus.ACTIVE) return false;
        if (!serviceConstraints.canHandle(request.parcelSpec())) return false;
        if (serviceConstraints.maxDistanceKm() > 0
                && request.distanceKm() > serviceConstraints.maxDistanceKm()) return false;
        if (request.desiredPickupAt() != null
                && !availability.isAvailableOn(request.desiredPickupAt())) return false;
        if (request.urgency() == DeliveryUrgency.EXPRESS && !availability.expressAvailable()) return false;
        if (request.urgency() == DeliveryUrgency.SAME_DAY && !availability.sameDayAvailable()) return false;
        return true;
    }

    /** Estimates the price for a given delivery request. */
    public Money estimatePrice(DeliveryRequest request) {
        PricingContext ctx = PricingContext.from(request, serviceType, tenantId);
        return pricingRules.estimate(ctx);
    }

    // -------------------------------------------------------
    // Getters
    // -------------------------------------------------------

    public ServiceOfferId getId() { return id; }
    public String getTenantId() { return tenantId; }
    public MarketListingId getListingId() { return listingId; }
    public UUID getProviderId() { return providerId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ServiceType getServiceType() { return serviceType; }
    public OfferStatus getStatus() { return status; }
    public PricingRules getPricingRules() { return pricingRules; }
    public ServiceConstraints getServiceConstraints() { return serviceConstraints; }
    public OfferAvailability getAvailability() { return availability; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
