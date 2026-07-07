package com.yowyob.tiibntick.core.product.domain;

import com.yowyob.tiibntick.core.product.domain.model.OfferComparison;
import com.yowyob.tiibntick.core.product.domain.model.ServiceOffer;
import com.yowyob.tiibntick.core.product.domain.model.ServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfferComparisonTest {

    private UUID tenantId;
    private UUID providerId;
    private ServiceOffer standard;
    private ServiceOffer express;
    private ServiceOffer freight;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        providerId = UUID.randomUUID();
        standard = ServiceOffer.create(tenantId, providerId, "Standard", null,
                ServiceType.STANDARD_DELIVERY, 100.0, null, 24, null, null).activate();
        express = ServiceOffer.create(tenantId, providerId, "Express", null,
                ServiceType.EXPRESS_DELIVERY, 30.0, 50.0, 4, null, null).activate();
        freight = ServiceOffer.create(tenantId, providerId, "Freight", null,
                ServiceType.FREIGHT_HEAVY, 500.0, 200.0, 72, null, null).activate();
    }

    @Test
    void shouldCompareBySlowestToFastest() {
        OfferComparison comparison = OfferComparison.of(List.of(freight, standard, express));
        List<ServiceOffer> sorted = comparison.compareByDeliverySpeed();
        assertThat(sorted.get(0).deliveryWindowHours()).isEqualTo(4);
        assertThat(sorted.get(2).deliveryWindowHours()).isEqualTo(72);
    }

    @Test
    void shouldCompareByCapacityDescending() {
        OfferComparison comparison = OfferComparison.of(List.of(express, standard, freight));
        List<ServiceOffer> sorted = comparison.compareByCapacity();
        assertThat(sorted.get(0).maxWeightKg()).isEqualTo(500.0);
    }

    @Test
    void shouldFilterMatchingForMission() {
        OfferComparison comparison = OfferComparison.of(List.of(standard, express, freight));
        List<ServiceOffer> matching = comparison.filterMatching(25.0, 30.0);
        assertThat(matching).containsExactlyInAnyOrder(standard, express, freight);
    }

    @Test
    void shouldFindBestMatchForMission() {
        OfferComparison comparison = OfferComparison.of(List.of(standard, express, freight));
        ServiceOffer best = comparison.bestMatch(25.0, 30.0);
        assertThat(best.type()).isEqualTo(ServiceType.EXPRESS_DELIVERY);
    }

    @Test
    void shouldRequireAtLeastTwoOffers() {
        assertThatThrownBy(() -> OfferComparison.of(List.of(standard)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2");
    }
}
