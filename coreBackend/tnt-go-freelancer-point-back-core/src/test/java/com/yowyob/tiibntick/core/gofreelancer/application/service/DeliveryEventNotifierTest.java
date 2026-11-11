package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Delivery events notify the people involved")
class DeliveryEventNotifierTest {

    @Mock private DeliveryRepository deliveryRepository;
    @Mock private IAnnouncementRepository announcementRepository;
    @Mock private DeliveryQueryUseCase deliveryQueryUseCase;
    @Mock private PushNotificationPort push;

    private DeliveryEventNotifier notifier;
    private final UUID tenant = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();
    private final UUID announcementId = UUID.randomUUID();
    private final UUID clientActor = UUID.randomUUID();
    private final UUID driverProfile = UUID.randomUUID();
    private final UUID driverActor = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        notifier = new DeliveryEventNotifier(deliveryRepository, announcementRepository, deliveryQueryUseCase, push);
        Delivery d = new Delivery();
        d.setId(deliveryId);
        d.setAnnouncementId(announcementId);
        d.setFreelancerId(driverProfile);
        when(deliveryRepository.findById(deliveryId)).thenReturn(Mono.just(d));
        Announcement a = new Announcement();
        a.setClientId(clientActor);
        when(announcementRepository.findById(announcementId)).thenReturn(Mono.just(a));
        when(deliveryQueryUseCase.resolveActorIdForDeliveryPerson(tenant, driverProfile)).thenReturn(Mono.just(driverActor));
        when(push.sendPushNotification(any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("PICKED_UP tells the client only, in their tenant, quoting the tracking code")
    void pickedUp_clientOnly() {
        StepVerifier.create(notifier.onMissionStatusChanged(UUID.randomUUID(), deliveryId, tenant, "PICKED_UP", "TNT-42"))
                .expectNext(1).verifyComplete();
        verify(push).sendPushNotification(eq(tenant), eq(clientActor), eq("Colis récupéré"), contains("TNT-42"));
        verify(push, never()).sendPushNotification(any(), eq(driverActor), any(), any());
    }

    @Test
    @DisplayName("an incident pause tells both the client and the livreur")
    void incidentPause_bothParties() {
        StepVerifier.create(notifier.onMissionStatusChanged(UUID.randomUUID(), deliveryId, tenant, "PAUSED_BY_INCIDENT", "TNT-42"))
                .expectNext(2).verifyComplete();
        verify(push).sendPushNotification(eq(tenant), eq(clientActor), eq("Livraison suspendue"), any());
        verify(push).sendPushNotification(eq(tenant), eq(driverActor), eq("Livraison suspendue"), any());
    }

    @Test
    @DisplayName("the same event delivered twice notifies once")
    void replay_isIgnored() {
        UUID event = UUID.randomUUID();
        notifier.onMissionStatusChanged(event, deliveryId, tenant, "DELIVERED", null).block();
        StepVerifier.create(notifier.onMissionStatusChanged(event, deliveryId, tenant, "DELIVERED", null))
                .expectNext(0).verifyComplete();
        verify(push, org.mockito.Mockito.times(1)).sendPushNotification(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a status that is not worth a notification, or an unknown delivery, sends nothing")
    void irrelevantOrUnknown() {
        StepVerifier.create(notifier.onMissionStatusChanged(UUID.randomUUID(), deliveryId, tenant, "CREATED", null))
                .expectNext(0).verifyComplete();
        UUID other = UUID.randomUUID();
        when(deliveryRepository.findById(other)).thenReturn(Mono.empty());
        StepVerifier.create(notifier.onMissionStatusChanged(UUID.randomUUID(), other, tenant, "DELIVERED", null))
                .expectNext(0).verifyComplete();
        verify(push, never()).sendPushNotification(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a failing notification does not break the event handling")
    void pushFailure_isTolerated() {
        when(push.sendPushNotification(any(), any(), any(), any())).thenReturn(Mono.error(new IllegalStateException("kernel down")));
        StepVerifier.create(notifier.onMissionStatusChanged(UUID.randomUUID(), deliveryId, tenant, "DELIVERED", null))
                .expectNext(1).verifyComplete();
    }

    @Test
    @DisplayName("a livreur whose actor cannot be resolved is skipped, the client is still told")
    void unresolvedDriver_isSkipped() {
        when(deliveryQueryUseCase.resolveActorIdForDeliveryPerson(tenant, driverProfile))
                .thenReturn(Mono.error(new IllegalStateException("no actor")));
        StepVerifier.create(notifier.onMissionStatusChanged(UUID.randomUUID(), deliveryId, tenant, "CANCELLED", null))
                .expectNext(1).verifyComplete();
        verify(push).sendPushNotification(eq(tenant), eq(clientActor), any(), any());
    }
}
