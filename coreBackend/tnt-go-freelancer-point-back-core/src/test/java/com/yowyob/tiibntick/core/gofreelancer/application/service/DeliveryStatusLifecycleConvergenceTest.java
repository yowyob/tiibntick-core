package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ISettlementUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.RecordSettlementCommand;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.CommissionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementMethod;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletSettlement;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort.CoreDeliveryState;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryStatusUpdateDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
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

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yowyob.tiibntick.core.delivery.domain.exception.InvalidDeliveryStateTransitionException;

/**
 * Convergence on tnt-delivery-core: every gofp transition goes through the core state machine first;
 * what the core refuses is never written on the gofp side, and gofp side-effects (OTP, trust anchors)
 * run only after the core accepted the move.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Status updates are decided by delivery-core")
class DeliveryStatusLifecycleConvergenceTest {

    @Mock private DeliveryRepository gofpDeliveryRepository;
    @Mock private RelayDepositService relayDepositService;
    @Mock private GofpRelayPointRepository gofpRelayPointRepository;
    @Mock private PushNotificationPort pushNotificationPort;
    @Mock private IDeliveryNeedRepository deliveryNeedRepository;
    @Mock private IAnnouncementRepository announcementRepository;
    @Mock private OtpService otpService;
    @Mock private DeliveryOtpService deliveryOtpService;
    @Mock private ISettlementUseCase settlements;
    @Mock private RecordCustodyTransferUseCase custodyTransferUseCase;
    @Mock private RecordMissionUseCase missionUseCase;
    @Mock private RecordPaymentUseCase paymentUseCase;
    @Mock private DeliveryQueryUseCase deliveryQueryUseCase;
    @Mock private IDeliveryLifecyclePort lifecycle;
    @Mock private TenantContextHolder tenantContextHolder;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();
    private final UUID driver = UUID.randomUUID();
    private final Instant pickupAt = Instant.parse("2026-11-11T10:00:00Z");

    private Delivery gofpDelivery;
    private final List<DeliveryStatus> writes = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        gofpDelivery = new Delivery();
        gofpDelivery.setId(deliveryId);
        gofpDelivery.setFreelancerId(driver);
        gofpDelivery.setStatus(DeliveryStatus.CREATED);
        gofpDelivery.setPickupOtpHash("pickup-hash");
        gofpDelivery.setDeliveryOtpHash("delivery-hash");

        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(gofpDeliveryRepository.findById(deliveryId)).thenReturn(Mono.just(gofpDelivery));
        when(gofpDeliveryRepository.save(any())).thenAnswer(inv -> {
            Delivery d = inv.getArgument(0);
            return Mono.fromSupplier(() -> { writes.add(d.getStatus()); return d; });
        });
        when(deliveryOtpService.initOtpIfAbsent(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(otpService.verifyOtp("111111", "pickup-hash")).thenReturn(true);
        when(custodyTransferUseCase.record(any())).thenReturn(Mono.empty());
        when(missionUseCase.recordCreated(any(), any(), any(), anyInt())).thenReturn(Mono.empty());
    }

    private void coreIsIn(String status) {
        when(lifecycle.findState(tenantId, deliveryId)).thenReturn(Mono.just(
                new CoreDeliveryState(deliveryId, status, "TNT-1", driver, null, null)));
    }

    private DeliveryStatusApplicationService service() {
        DeliveryStatusApplicationService s = new DeliveryStatusApplicationService(
                gofpDeliveryRepository, relayDepositService, gofpRelayPointRepository,
                pushNotificationPort, deliveryNeedRepository, announcementRepository,
                otpService, deliveryOtpService, settlements,
                custodyTransferUseCase, missionUseCase, paymentUseCase,
                deliveryQueryUseCase, lifecycle, tenantContextHolder, new SimpleMeterRegistry());
        s.initMetrics();
        return s;
    }

    private static DeliveryStatusUpdateDTO dto(DeliveryStatus status) {
        DeliveryStatusUpdateDTO d = new DeliveryStatusUpdateDTO();
        d.setStatus(status);
        return d;
    }

    @Test
    @DisplayName("PICKED_UP with the sender's code: the core confirms pickup, the row mirrors it with the pickup instant, custody is anchored")
    void pickup_goesThroughTheCore_thenProjects() {
        coreIsIn("CREATED");
        when(lifecycle.confirmPickup(tenantId, deliveryId, driver)).thenReturn(Mono.just(
                new CoreDeliveryState(deliveryId, "PICKED_UP", "TNT-1", driver, pickupAt, null)));
        DeliveryStatusUpdateDTO d = dto(DeliveryStatus.PICKED_UP);
        d.setConfirmationCode("111111");

        StepVerifier.create(service().updateStatus(deliveryId, d))
                .assertNext(saved -> {
                    assertThat(saved.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
                    assertThat(saved.getActualPickupTime()).isEqualTo(pickupAt);
                })
                .verifyComplete();

        assertThat(writes).containsExactly(DeliveryStatus.PICKED_UP);
        verify(custodyTransferUseCase).record(any());
    }

    @Test
    @DisplayName("PICKED_UP with a wrong code: refused before the core is touched")
    void pickup_wrongCode_neverReachesTheCore() {
        coreIsIn("CREATED");
        DeliveryStatusUpdateDTO d = dto(DeliveryStatus.PICKED_UP);
        d.setConfirmationCode("000000");

        StepVerifier.create(service().updateStatus(deliveryId, d))
                .expectErrorMatches(e -> e instanceof IllegalArgumentException && e.getMessage().contains("incorrect"))
                .verify();

        verify(lifecycle, never()).confirmPickup(any(), any(), any());
        assertThat(writes).isEmpty();
    }

    @Test
    @DisplayName("PICKED_UP without any code: refused")
    void pickup_missingCode_refused() {
        coreIsIn("CREATED");
        StepVerifier.create(service().updateStatus(deliveryId, dto(DeliveryStatus.PICKED_UP)))
                .expectError(IllegalArgumentException.class).verify();
        verify(lifecycle, never()).confirmPickup(any(), any(), any());
    }

    @Test
    @DisplayName("a move the core state machine refuses is propagated and nothing is written or anchored")
    void illegalCoreTransition_isPropagated_andNothingWritten() {
        coreIsIn("CREATED");
        when(lifecycle.confirmPickup(tenantId, deliveryId, driver)).thenReturn(Mono.error(
                new InvalidDeliveryStateTransitionException(
                        com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus.CREATED,
                        com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus.PICKED_UP)));
        DeliveryStatusUpdateDTO d = dto(DeliveryStatus.PICKED_UP);
        d.setConfirmationCode("111111");

        StepVerifier.create(service().updateStatus(deliveryId, d))
                .expectError(InvalidDeliveryStateTransitionException.class).verify();

        assertThat(writes).isEmpty();
        verify(custodyTransferUseCase, never()).record(any());
    }

    @Test
    @DisplayName("IN_TRANSIT from PICKED_UP: started in the core and the mission creation is anchored")
    void inTransit_startsAndAnchorsMissionCreation() {
        coreIsIn("PICKED_UP");
        when(lifecycle.startOrResumeTransit(tenantId, deliveryId, driver)).thenReturn(Mono.just(
                new CoreDeliveryState(deliveryId, "IN_TRANSIT", "TNT-1", driver, pickupAt, null)));

        StepVerifier.create(service().updateStatus(deliveryId, dto(DeliveryStatus.IN_TRANSIT)))
                .assertNext(saved -> assertThat(saved.getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT))
                .verifyComplete();

        verify(missionUseCase).recordCreated(eq(deliveryId.toString()), eq(driver.toString()), any(), anyInt());
    }

    @Test
    @DisplayName("IN_TRANSIT from AT_RELAY_POINT is a resume: no second mission-created anchor")
    void inTransit_resumeFromRelay_doesNotAnchorAgain() {
        coreIsIn("AT_RELAY_POINT");
        when(lifecycle.startOrResumeTransit(tenantId, deliveryId, driver)).thenReturn(Mono.just(
                new CoreDeliveryState(deliveryId, "IN_TRANSIT", "TNT-1", driver, pickupAt, null)));

        StepVerifier.create(service().updateStatus(deliveryId, dto(DeliveryStatus.IN_TRANSIT)))
                .assertNext(saved -> assertThat(saved.getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT))
                .verifyComplete();

        verify(missionUseCase, never()).recordCreated(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("DELIVERED straight after pickup: the transit is started first because the core refuses PICKED_UP -> DELIVERED")
    void deliveredAfterPickup_startsTransitFirst() {
        coreIsIn("PICKED_UP");
        gofpDelivery.setStatus(DeliveryStatus.PICKED_UP);
        when(otpService.verifyOtp("222222", "delivery-hash")).thenReturn(true);
        when(lifecycle.startOrResumeTransit(tenantId, deliveryId, driver)).thenReturn(Mono.just(
                new CoreDeliveryState(deliveryId, "IN_TRANSIT", "TNT-1", driver, pickupAt, null)));
        when(lifecycle.complete(tenantId, deliveryId, driver)).thenReturn(Mono.just(
                new CoreDeliveryState(deliveryId, "DELIVERED", "TNT-1", driver, pickupAt, pickupAt.plusSeconds(600))));
        when(missionUseCase.recordCompleted(any(), any(), any())).thenReturn(Mono.empty());
        when(deliveryQueryUseCase.resolveActorIdForDeliveryPerson(tenantId, driver)).thenReturn(Mono.just(driver));
        when(settlements.record(any())).thenReturn(Mono.empty());
        DeliveryStatusUpdateDTO d = dto(DeliveryStatus.DELIVERED);
        d.setConfirmationCode("222222");

        StepVerifier.create(service().updateStatus(deliveryId, d))
                .assertNext(saved -> assertThat(saved.getStatus()).isEqualTo(DeliveryStatus.DELIVERED))
                .verifyComplete();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(lifecycle);
        order.verify(lifecycle).startOrResumeTransit(tenantId, deliveryId, driver);
        order.verify(lifecycle).complete(tenantId, deliveryId, driver);
        assertThat(writes).as("a single DELIVERED write").containsExactly(DeliveryStatus.DELIVERED);
    }

    @Test
    @DisplayName("FAILED carries the reason to the core and the row follows")
    void failed_recordsReasonInTheCore() {
        coreIsIn("IN_TRANSIT");
        when(lifecycle.fail(tenantId, deliveryId, driver, "Recipient absent")).thenReturn(Mono.just(
                new CoreDeliveryState(deliveryId, "FAILED", "TNT-1", driver, pickupAt, null)));
        DeliveryStatusUpdateDTO d = dto(DeliveryStatus.FAILED);
        d.setReason("Recipient absent");

        StepVerifier.create(service().updateStatus(deliveryId, d))
                .assertNext(saved -> assertThat(saved.getStatus()).isEqualTo(DeliveryStatus.FAILED))
                .verifyComplete();
    }

    @Test
    @DisplayName("CANCELLED goes to the core cancellation")
    void cancelled_goesToTheCore() {
        coreIsIn("CREATED");
        when(lifecycle.cancel(eq(tenantId), eq(deliveryId), eq(driver), any())).thenReturn(Mono.just(
                new CoreDeliveryState(deliveryId, "CANCELLED", "TNT-1", driver, null, null)));

        StepVerifier.create(service().updateStatus(deliveryId, dto(DeliveryStatus.CANCELLED)))
                .assertNext(saved -> assertThat(saved.getStatus()).isEqualTo(DeliveryStatus.CANCELLED))
                .verifyComplete();
    }

    @Test
    @DisplayName("a status that is only reached by the lifecycle (e.g. PAUSED_BY_INCIDENT) cannot be forced from the API")
    void lifecycleOnlyStatuses_cannotBeForced() {
        coreIsIn("IN_TRANSIT");
        StepVerifier.create(service().updateStatus(deliveryId, dto(DeliveryStatus.PAUSED_BY_INCIDENT)))
                .expectError(IllegalArgumentException.class).verify();
        assertThat(writes).isEmpty();
    }

    @Test
    @DisplayName("AT_RELAY_POINT without a relay point is refused")
    void relayDeposit_requiresRelayPoint() {
        coreIsIn("IN_TRANSIT");
        StepVerifier.create(service().updateStatus(deliveryId, dto(DeliveryStatus.AT_RELAY_POINT)))
                .expectError(IllegalArgumentException.class).verify();
    }

    @Test
    @DisplayName("a delivery that has no core counterpart is reported as not found, never silently updated")
    void missingCoreDelivery_isNotFound() {
        when(lifecycle.findState(tenantId, deliveryId)).thenReturn(Mono.empty());
        StepVerifier.create(service().updateStatus(deliveryId, dto(DeliveryStatus.IN_TRANSIT)))
                .expectErrorMatches(e -> e instanceof IllegalArgumentException && e.getMessage().contains("not found"))
                .verify();
        assertThat(writes).isEmpty();
    }
}
