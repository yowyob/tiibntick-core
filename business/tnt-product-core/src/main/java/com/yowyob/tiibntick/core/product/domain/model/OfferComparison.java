package com.yowyob.tiibntick.core.product.domain.model;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Value Object: OfferComparison.
 *
 * Encapsulates a set of comparable ServiceOffer records and provides
 * domain-level comparison operations for client-facing offer selection.
 *
 * @author MANFOUO Braun.
 */
public final class OfferComparison {

    private final List<ServiceOffer> offers;

    private OfferComparison(List<ServiceOffer> offers) {
        Objects.requireNonNull(offers, "offers must not be null");
        if (offers.size() < 2) {
            throw new IllegalArgumentException("OfferComparison requires at least 2 offers to compare");
        }
        this.offers = List.copyOf(offers);
    }

    public static OfferComparison of(List<ServiceOffer> offers) {
        return new OfferComparison(offers);
    }

    /**
     * Returns offers sorted by maximum weight capacity (descending = heaviest first).
     */
    public List<ServiceOffer> compareByCapacity() {
        return offers.stream()
                .sorted(Comparator.comparingDouble(ServiceOffer::maxWeightKg).reversed())
                .toList();
    }

    /**
     * Returns offers sorted by delivery window (ascending = fastest first).
     */
    public List<ServiceOffer> compareByDeliverySpeed() {
        return offers.stream()
                .sorted(Comparator.comparingInt(ServiceOffer::deliveryWindowHours))
                .toList();
    }

    /**
     * Returns offers of a specific service type.
     */
    public List<ServiceOffer> filterByType(ServiceType type) {
        return offers.stream()
                .filter(o -> o.type() == type)
                .toList();
    }

    /**
     * Returns offers that match a given mission requirement.
     */
    public List<ServiceOffer> filterMatching(double weightKg, double distanceKm) {
        return offers.stream()
                .filter(o -> o.matchesMission(weightKg, distanceKm))
                .toList();
    }

    /**
     * Returns the best matching offer for a given mission (fastest delivery among eligible).
     */
    public ServiceOffer bestMatch(double weightKg, double distanceKm) {
        return filterMatching(weightKg, distanceKm).stream()
                .min(Comparator.comparingInt(ServiceOffer::deliveryWindowHours))
                .orElseThrow(() -> new IllegalStateException(
                        "No matching offer found for weight=" + weightKg + " dist=" + distanceKm));
    }

    public List<ServiceOffer> offers() { return offers; }
    public int size() { return offers.size(); }
}
