package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ISettlementUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryStatusUpdateDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort.CoreDeliveryState;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayDeposit;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordCustodyTransferUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordMissionUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordPaymentUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deposit of a parcel at a relay point: the client who will collect it is the owner of the
 * delivery need, the relay point is the one the sender chose, and that relay point is open.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Relay deposit rules")
class RelayDepositRulesTest {

    @Mock private DeliveryRepository gofpDeliveryRepository;
    @Mock private RelayDepositService relayDepositService;
    @Mock private GofpRelayPointRepository relayPoints;
    @Mock private PushNotificationPort push;
    @Mock private IDeliveryNeedRepository needs;
    @Mock private IAnnouncementRepository announcements;
    @Mock private OtpService otpService;
    @Mock private DeliveryOtpService deliveryOtpService;
    @Mock private ISettlementUseCase settlements;
    @Mock private RecordCustodyTransferUseCase custody;
    @Mock private RecordMissionUseCase missions;
    @Mock private RecordPaymentUseCase payments;
    @Mock private DeliveryQueryUseCase deliveryQuery;
    @Mock private IDeliveryLifecyclePort lifecycle;
    @Mock private TenantContextHolder tenantContextHolder;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();
    private final UUID needId = UUID.randomUUID();
    private final UUID driver = UUID.randomUUID();
    private final UUID sender = UUID.randomUUID();
    private final UUID chosenProfile = UUID.randomUUID();
    private final UUID chosenHub = UUID.randomUUID();
    private final UUID otherProfile = UUID.randomUUID();
    private final UUID otherHub = UUID.randomUUID();

    private DeliveryNeed need;
    private GofpRelayPoint chosen;
    private GofpRelayPoint other;

    @BeforeEach
    void setUp() {
        Delivery delivery = new Delivery();
        delivery.setId(deliveryId);
        com.yowyob.tiibntick.core.gofreelancer.TestDeliveries.driver(delivery, driver);
        delivery.setDeliveryNeedId(needId);
        com.yowyob.tiibntick.core.gofreelancer.TestDeliveries.status(delivery, DeliveryStatus.IN_TRANSIT);

        need = new DeliveryNeed();
        need.setId(needId);
        need.setUserId(sender);
        need.setTargetRelayPointId(chosenProfile);

        chosen = relayPoint(chosenProfile, chosenHub);
        other = relayPoint(otherProfile, otherHub);

        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(gofpDeliveryRepository.findById(deliveryId)).thenReturn(Mono.just(delivery));
        when(gofpDeliveryRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(deliveryOtpService.initOtpIfAbsent(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(needs.findById(needId)).thenReturn(Mono.just(need));
        when(lifecycle.findState(tenantId, deliveryId)).thenReturn(Mono.just(
                new CoreDeliveryState(deliveryId, "IN_TRANSIT", "TNT-1", driver, null, null)));
        when(lifecycle.depositAtRelayHub(eq(tenantId), eq(deliveryId), eq(driver), any(), any())).thenReturn(Mono.just(
                new CoreDeliveryState(deliveryId, "AT_RELAY_HUB", "TNT-1", driver, null, null)));
        when(relayDepositService.createRelayDeposit(any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(new RelayDeposit()));
        when(custody.record(any())).thenReturn(Mono.empty());
        when(missions.recordCompleted(any(), any(), any())).thenReturn(Mono.empty());
        when(missions.recordCreated(any(), any(), any(), anyInt())).thenReturn(Mono.empty());
        for (GofpRelayPoint rp : new GofpRelayPoint[] {chosen, other}) {
            when(relayPoints.findById(rp.getId())).thenReturn(Mono.just(rp));
            when(relayPoints.findById(rp.getCoreRelayPointId())).thenReturn(Mono.empty());
            when(relayPoints.findByCoreRelayPointId(rp.getCoreRelayPointId())).thenReturn(Mono.just(rp));
            when(relayPoints.findByCoreRelayPointId(rp.getId())).thenReturn(Mono.empty());
        }
    }

    private static GofpRelayPoint relayPoint(UUID id, UUID hub) {
        GofpRelayPoint rp = new GofpRelayPoint();
        rp.setId(id);
        rp.setCoreRelayPointId(hub);
        rp.setName("Point");
        rp.setStatus(RelayPointStatus.APPROVED);
        rp.setIsActive(true);
        return rp;
    }

    private DeliveryStatusApplicationService service() {
        DeliveryStatusApplicationService s = new DeliveryStatusApplicationService(
                gofpDeliveryRepository, relayDepositService, relayPoints, push, needs, announcements,
                otpService, deliveryOtpService, settlements, custody, missions, payments,
                deliveryQuery, lifecycle, tenantContextHolder, new SimpleMeterRegistry());
        s.initMetrics();
        return s;
    }

    private static DeliveryStatusUpdateDTO deposit(UUID relayPointId, UUID clientId) {
        DeliveryStatusUpdateDTO d = new DeliveryStatusUpdateDTO();
        d.setStatus(DeliveryStatus.AT_RELAY_HUB);
        d.setRelayPointId(relayPointId);
        d.setClientId(clientId);
        return d;
    }

    @Test
    @DisplayName("D-149: a deposit at the destination relay ends the mission (anchoring 'mission completed')")
    void depositAtDestinationEndsTheMission() {
        when(lifecycle.depositAtRelayHub(eq(tenantId), eq(deliveryId), eq(driver), any(), any())).thenReturn(Mono.just(
                new CoreDeliveryState(deliveryId, "AT_RELAY_HUB", "TNT-1", driver, null, null, null, null, null,
                        null, null, null, null, null, null, 3, java.time.Instant.now().plusSeconds(86_400), true)));
        StepVerifier.create(service().updateStatus(deliveryId, deposit(chosenProfile, null)))
                .expectNextCount(1).verifyComplete();
        verify(missions).recordCompleted(eq(deliveryId.toString()), eq(driver.toString()), any());
    }

    @Test
    @DisplayName("D-149: a deposit at a relay on the way ends nothing: no anchoring, no payment")
    void depositOnTheWayEndsNothing() {
        StepVerifier.create(service().updateStatus(deliveryId, deposit(chosenProfile, null)))
                .expectNextCount(1).verifyComplete();
        verify(missions, never()).recordCompleted(any(), any(), any());
        org.mockito.Mockito.verifyNoInteractions(settlements);
        verify(relayDepositService).createRelayDeposit(any(), any(), eq(chosenHub), any(), eq(driver));
    }

    @Test
    @DisplayName("the deposit is recorded for the owner of the delivery need, whatever clientId the request carries")
    void clientIsTheNeedOwner_notTheRequestBody() {
        UUID stranger = UUID.randomUUID();
        StepVerifier.create(service().updateStatus(deliveryId, deposit(chosenProfile, stranger)))
                .expectNextCount(1).verifyComplete();
        ArgumentCaptor<UUID> client = ArgumentCaptor.forClass(UUID.class);
        verify(relayDepositService).createRelayDeposit(eq(needId), client.capture(), eq(chosenHub), any(), eq(driver));
        assertThat(client.getValue()).isEqualTo(sender);
    }

    @Test
    @DisplayName("lot L1: the deposit is keyed on the parcel the need registered, not on the need")
    void depositKey_isTheNeedParcel() {
        UUID parcel = UUID.randomUUID();
        need.setPacketId(parcel);
        StepVerifier.create(service().updateStatus(deliveryId, deposit(chosenProfile, null)))
                .expectNextCount(1).verifyComplete();
        verify(relayDepositService).createRelayDeposit(eq(parcel), eq(sender), eq(chosenHub), any(), eq(driver));
    }

    @Test
    @DisplayName("lot L1: the parcel the core delivery carries is the deposit key (same id from the need to the hub)")
    void depositKey_isTheCoreParcel() {
        UUID parcel = UUID.randomUUID();
        need.setPacketId(parcel);
        when(lifecycle.findState(tenantId, deliveryId)).thenReturn(Mono.just(new CoreDeliveryState(
                deliveryId, "IN_TRANSIT", "TNT-1", driver, null, null, null, null, null, null, null, null,
                null, parcel, null)));
        StepVerifier.create(service().updateStatus(deliveryId, deposit(chosenProfile, null)))
                .expectNextCount(1).verifyComplete();
        verify(relayDepositService).createRelayDeposit(eq(parcel), eq(sender), eq(chosenHub), any(), eq(driver));
        verify(relayDepositService, never()).createRelayDeposit(eq(needId), any(), any(), any(), any());
    }

    @Test
    @DisplayName("the request does not need to carry a clientId at all")
    void clientIdIsNotRequired() {
        StepVerifier.create(service().updateStatus(deliveryId, deposit(chosenHub, null)))
                .expectNextCount(1).verifyComplete();
        verify(relayDepositService).createRelayDeposit(eq(needId), eq(sender), eq(chosenHub), any(), eq(driver));
    }

    @Test
    @DisplayName("a relay point other than the one chosen by the sender is refused, nothing is written")
    void otherRelayPoint_isRefused() {
        StepVerifier.create(service().updateStatus(deliveryId, deposit(otherProfile, sender)))
                .expectError(IllegalArgumentException.class).verify();
        verify(lifecycle, never()).depositAtRelayHub(any(), any(), any(), any(), any());
        verify(relayDepositService, never()).createRelayDeposit(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a delivery whose sender chose no relay point cannot be deposited at one")
    void noChosenRelayPoint_isRefused() {
        need.setTargetRelayPointId(null);
        StepVerifier.create(service().updateStatus(deliveryId, deposit(chosenProfile, sender)))
                .expectError(IllegalArgumentException.class).verify();
        verify(lifecycle, never()).depositAtRelayHub(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a relay point that is not approved refuses the deposit")
    void notApproved_isRefused() {
        chosen.setStatus(RelayPointStatus.PENDING);
        StepVerifier.create(service().updateStatus(deliveryId, deposit(chosenProfile, sender)))
                .expectError(IllegalArgumentException.class).verify();
        verify(lifecycle, never()).depositAtRelayHub(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("an inactive relay point refuses the deposit")
    void inactive_isRefused() {
        chosen.setIsActive(false);
        StepVerifier.create(service().updateStatus(deliveryId, deposit(chosenProfile, sender)))
                .expectError(IllegalArgumentException.class).verify();
        verify(lifecycle, never()).depositAtRelayHub(any(), any(), any(), any(), any());
    }
}
