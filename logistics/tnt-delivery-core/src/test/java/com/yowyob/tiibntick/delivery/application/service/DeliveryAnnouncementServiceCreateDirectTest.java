package com.yowyob.tiibntick.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDirectDeliveryCommand;
import com.yowyob.tiibntick.core.delivery.application.port.out.*;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryAnnouncementService;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeliveryAnnouncementService#createDirect}, the entry point that lets
 * a caller (e.g. an agency dispatching from a client intake) create a real {@code Delivery}
 * without going through the announcement/response-selection marketplace flow.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class DeliveryAnnouncementServiceCreateDirectTest {

    @Mock private DeliveryAnnouncementRepository announcementRepository;
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private DeliveryPersonRepository deliveryPersonRepository;
    @Mock private DeliveryCostComputationPort costComputationPort;
    @Mock private EtaComputationPort etaComputationPort;
    @Mock private DeliveryEventPublisher eventPublisher;

    private DeliveryAnnouncementService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryAnnouncementService(
                announcementRepository, deliveryRepository, deliveryPersonRepository,
                costComputationPort, etaComputationPort, eventPublisher);
    }

    private CreateDirectDeliveryCommand command(UUID tenantId, UUID senderId, UUID agencyId) {
        return new CreateDirectDeliveryCommand(
                tenantId, senderId, agencyId,
                DeliveryAddress.informal("Rue de la Joie", "Bonamoussadi", "Douala", null),
                DeliveryAddress.informal("Rue du Marché", "Akwa", "Douala", null),
                new RecipientInfo("Marie Recipient", "+237690000002", null),
                DeliveryUrgency.STANDARD,
                new PackageSpecification(2.5, 30, 20, 15, false, false, "Parcel"),
                null, null);
    }

    @Test
    @DisplayName("createDirect() persists a Delivery with a generated trackingCode and no announcementId")
    void createDirectPersistsDeliveryWithoutAnnouncement() {
        UUID tenantId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();

        when(deliveryRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishAll(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.createDirect(command(tenantId, senderId, agencyId)))
                .assertNext(delivery -> {
                    assertThat(delivery.getAnnouncementId()).isNull();
                    assertThat(delivery.getSenderId()).isEqualTo(senderId);
                    assertThat(delivery.getTrackingCode()).matches("TNT-\\d{8}-[A-Z0-9]{8}");
                    assertThat(delivery.getAgencyId()).isEqualTo(agencyId);
                    assertThat(delivery.getPlatform()).isEqualTo("AGENCY");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("createDirect() does not set an agency context when agencyId is null")
    void createDirectWithoutAgencyIdLeavesAgencyContextUnset() {
        UUID tenantId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        when(deliveryRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishAll(any())).thenReturn(Mono.empty());

        Mono<Delivery> result = service.createDirect(command(tenantId, senderId, null));

        StepVerifier.create(result)
                .assertNext(delivery -> assertThat(delivery.getAgencyId()).isNull())
                .verifyComplete();
    }
}
