package com.yowyob.tiibntick.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryThresholdsPort;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryTransitionExecutor;
import com.yowyob.tiibntick.core.delivery.application.service.ReturnDeliveryService;

import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.out.*;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryAnnouncementService;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementPricingMode;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** The announcement reuses a parcel registered beforehand, so its id follows the goods. */
@ExtendWith(MockitoExtension.class)
class DeliveryAnnouncementServicePublishParcelTest {

    @Mock private DeliveryAnnouncementRepository announcementRepository;
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private DeliveryPersonRepository deliveryPersonRepository;
    @Mock private DeliveryCostComputationPort costComputationPort;
    @Mock private EtaComputationPort etaComputationPort;
    @Mock private DeliveryEventPublisher eventPublisher;

    private DeliveryAnnouncementService service;

    @BeforeEach
    void setUp() {
        DeliveryThresholdsPort thresholds = org.mockito.Mockito.mock(DeliveryThresholdsPort.class);
        DeliveryTransitionExecutor executor = new DeliveryTransitionExecutor(deliveryRepository,
                announcementRepository, eventPublisher, new ReturnDeliveryService(announcementRepository, thresholds));
        service = new DeliveryAnnouncementService(
                announcementRepository, deliveryRepository, deliveryPersonRepository,
                costComputationPort, etaComputationPort, executor, thresholds);
        when(announcementRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishAll(any())).thenReturn(Mono.empty());
    }

    private CreateDeliveryAnnouncementCommand command(Parcel existing) {
        return new CreateDeliveryAnnouncementCommand(
                UUID.randomUUID(), UUID.randomUUID(), "Need", null, new BigDecimal("1000"), "XAF",
                AnnouncementPricingMode.FIXED_PRICE,
                new PackageSpecification(1, 10, 10, 10, false, false, "pkg"),
                DeliveryAddress.informal("Rue A", "D", "Douala", null),
                DeliveryAddress.informal("Rue B", "D", "Douala", null),
                new RecipientInfo("Marie", "+237690000002", null),
                DeliveryUrgency.STANDARD, existing);
    }

    @Test
    @DisplayName("an existing parcel is kept: the announcement carries the same parcel id")
    void reusesExistingParcel() {
        Parcel existing = Parcel.create(new PackageSpecification(3, 30, 20, 10, true, false, "registered"));

        StepVerifier.create(service.publishAnnouncement(command(existing)))
                .assertNext(a -> assertThat(a.getParcel().getId()).isEqualTo(existing.getId()))
                .verifyComplete();
    }

    @Test
    @DisplayName("without an existing parcel a new one is created from the specification")
    void createsParcelOtherwise() {
        StepVerifier.create(service.publishAnnouncement(command(null)))
                .assertNext(a -> {
                    assertThat(a.getParcel().getId()).isNotNull();
                    assertThat(a.getParcel().getSpecification().description()).isEqualTo("pkg");
                })
                .verifyComplete();
    }
}
