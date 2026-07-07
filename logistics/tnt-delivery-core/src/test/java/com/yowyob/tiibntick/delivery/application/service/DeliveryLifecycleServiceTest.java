package com.yowyob.tiibntick.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.in.command.CancelDeliveryCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.ConfirmPickupCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.StartTransitCommand;
import com.yowyob.tiibntick.core.delivery.application.port.out.*;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryLifecycleService;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.*;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryNotFoundException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsClass;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@code DeliveryLifecycleService} using Mockito and StepVerifier.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class DeliveryLifecycleServiceTest {

    @Mock
    DeliveryRepository deliveryRepository;
    @Mock
    DeliveryPersonRepository deliveryPersonRepository;
    @Mock
    EtaComputationPort etaComputationPort;
    @Mock
    DeliveryCostComputationPort costComputationPort;
    @Mock
    DeliveryEventPublisher eventPublisher;

    @InjectMocks
    DeliveryLifecycleService service;

    private static final UUID TENANT_ID          = UUID.randomUUID();
    private static final UUID DELIVERY_ID         = UUID.randomUUID();
    private static final UUID DELIVERY_PERSON_ID  = UUID.randomUUID();

    private Delivery deliveryCreated;
    private Delivery deliveryPickedUp;
    private DeliveryPerson deliveryPerson;

    @BeforeEach
    void setUp() {
        Parcel parcel = Parcel.create(new PackageSpecification(2.0, 25, 20, 15, false, false, "Test"));
        deliveryCreated = Delivery.create(TENANT_ID, null, UUID.randomUUID(), parcel,
                addr("Mokolo"), addr("Bastos"),
                new RecipientInfo("Test User", "+237690000001", null),
                DeliveryUrgency.STANDARD, null, null);
        deliveryCreated.assignDeliveryPerson(DELIVERY_PERSON_ID, null, 5.0,
                Instant.now().plusSeconds(3600));

        deliveryPickedUp = Delivery.create(TENANT_ID, null, UUID.randomUUID(), parcel,
                addr("Mokolo"), addr("Bastos"),
                new RecipientInfo("Test User", "+237690000001", null),
                DeliveryUrgency.STANDARD, null, null);
        deliveryPickedUp.assignDeliveryPerson(DELIVERY_PERSON_ID, null, 5.0,
                Instant.now().plusSeconds(3600));
        deliveryPickedUp.confirmPickup();

        deliveryPerson = DeliveryPerson.register(TENANT_ID, UUID.randomUUID(),
                LogisticsType.MOTORBIKE, LogisticsClass.STANDARD,
                50.0, 0.0, 0, "Rouge", "REG-001");
        deliveryPerson.approve();
    }

    @Test
    @DisplayName("confirmPickup should transition CREATED to PICKED_UP")
    void shouldConfirmPickup() {
        when(deliveryRepository.findById(TENANT_ID, DELIVERY_ID)).thenReturn(Mono.just(deliveryCreated));
        when(deliveryRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishAll(any())).thenReturn(Mono.empty());

        ConfirmPickupCommand cmd = new ConfirmPickupCommand(TENANT_ID, DELIVERY_ID, DELIVERY_PERSON_ID);

        StepVerifier.create(service.confirmPickup(cmd))
                .assertNext(d -> {
                    assertThat(d.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
                    assertThat(d.getActualPickupTime()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("confirmPickup for non-existent delivery should emit DeliveryNotFoundException")
    void shouldErrorWhenDeliveryNotFound() {
        when(deliveryRepository.findById(TENANT_ID, DELIVERY_ID)).thenReturn(Mono.empty());

        ConfirmPickupCommand cmd = new ConfirmPickupCommand(TENANT_ID, DELIVERY_ID, DELIVERY_PERSON_ID);

        StepVerifier.create(service.confirmPickup(cmd))
                .expectError(DeliveryNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("startTransit should compute ETA and transition PICKED_UP to IN_TRANSIT")
    void shouldStartTransit() {
        EtaEstimate eta = EtaEstimate.of(Instant.now().plusSeconds(1800), 5.0, 30);
        when(deliveryRepository.findById(TENANT_ID, DELIVERY_ID)).thenReturn(Mono.just(deliveryPickedUp));
        when(deliveryRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishAll(any())).thenReturn(Mono.empty());
        when(etaComputationPort.computeInitial(any(), any(), anyDouble())).thenReturn(Mono.just(eta));

        StartTransitCommand cmd = new StartTransitCommand(TENANT_ID, DELIVERY_ID,
                DELIVERY_PERSON_ID, new GeoCoordinates(3.87, 11.516));

        StepVerifier.create(service.startTransit(cmd))
                .assertNext(d -> {
                    assertThat(d.getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
                    assertThat(d.getCurrentEta()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("cancelDelivery should transition CREATED to CANCELLED")
    void shouldCancelDelivery() {
        when(deliveryRepository.findById(TENANT_ID, DELIVERY_ID)).thenReturn(Mono.just(deliveryCreated));
        when(deliveryRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishAll(any())).thenReturn(Mono.empty());

        CancelDeliveryCommand cmd = new CancelDeliveryCommand(TENANT_ID, DELIVERY_ID,
                UUID.randomUUID(), "Client changed mind");

        StepVerifier.create(service.cancelDelivery(cmd))
                .assertNext(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.CANCELLED))
                .verifyComplete();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static DeliveryAddress addr(String landmark) {
        return new DeliveryAddress(null, landmark, landmark, "Yaoundé", "CM",
                new GeoCoordinates(3.87, 11.52));
    }
}
