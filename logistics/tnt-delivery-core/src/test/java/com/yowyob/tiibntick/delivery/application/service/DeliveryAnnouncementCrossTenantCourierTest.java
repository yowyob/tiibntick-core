package com.yowyob.tiibntick.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.in.command.RespondToAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryAnnouncementRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryCostComputationPort;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryEventPublisher;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryPersonRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.EtaComputationPort;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryAnnouncementService;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot C-56 — the Go marketplace lists and invites couriers of every tenant: a courier whose
 * delivery-person row lives in another tenant than the announcement must still be able to answer
 * it (before: « Delivery person is not available », 409).
 */
@DisplayName("C-56 — courier of another tenant answers an announcement")
class DeliveryAnnouncementCrossTenantCourierTest {

    private final DeliveryAnnouncementRepository announcements = mock(DeliveryAnnouncementRepository.class);
    private final DeliveryPersonRepository persons = mock(DeliveryPersonRepository.class);
    private DeliveryAnnouncementService service;

    private final UUID announcementTenant = UUID.randomUUID();
    private final UUID announcementId = UUID.randomUUID();
    private final UUID courierId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DeliveryAnnouncementService(announcements, mock(DeliveryRepository.class), persons,
                mock(DeliveryCostComputationPort.class), mock(EtaComputationPort.class),
                mock(DeliveryEventPublisher.class));
    }

    private RespondToAnnouncementCommand respond() {
        return new RespondToAnnouncementCommand(announcementTenant, announcementId, courierId,
                Instant.now().plusSeconds(1800), null, new BigDecimal("750"), "XAF");
    }

    @Test
    @DisplayName("courier row in another tenant → found by its id, the response is saved")
    void courierOfAnotherTenantCanRespond() {
        DeliveryAnnouncement announcement = mock(DeliveryAnnouncement.class);
        DeliveryPerson courier = mock(DeliveryPerson.class);
        when(courier.isAvailable()).thenReturn(true);
        when(announcements.findById(announcementTenant, announcementId)).thenReturn(Mono.just(announcement));
        when(announcements.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(persons.findById(announcementTenant, courierId)).thenReturn(Mono.empty());
        when(persons.findByIdInAnyTenant(courierId)).thenReturn(Mono.just(courier));

        StepVerifier.create(service.respondToAnnouncement(respond())).expectNext(announcement).verifyComplete();
        verify(announcement).addResponse(any());
    }

    @Test
    @DisplayName("courier unknown everywhere → still refused (not available)")
    void unknownCourierStillRefused() {
        when(announcements.findById(announcementTenant, announcementId))
                .thenReturn(Mono.just(mock(DeliveryAnnouncement.class)));
        when(persons.findById(announcementTenant, courierId)).thenReturn(Mono.empty());
        when(persons.findByIdInAnyTenant(courierId)).thenReturn(Mono.empty());

        StepVerifier.create(service.respondToAnnouncement(respond()))
                .expectError(DeliveryDomainException.class).verify();
    }
}
