package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ISettlementUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.RecordSettlementCommand;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.CommissionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementMethod;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletSettlement;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What completing a direct delivery does about payment after the Kernel wallet migration
 * (supersedes lots C-20.3 / C-20.4, which pinned the local-ledger failure modes).
 *
 * <p>No money moves at completion any more — the client pays from their own Kernel wallet through
 * the challenge/confirm flow. Completion only reconciles:
 * <ul>
 *   <li>already PAID by transfer → nothing to do, payment anchored on the blockchain;</li>
 *   <li>digital but not paid → the delivery stays DELIVERED, the call SUCCEEDS, the unpaid state
 *       is recorded, counted ({@code gofp.delivery.payment.failures}) and logged
 *       {@code UNPAID_DELIVERED}; nothing is anchored;</li>
 *   <li>CASH → cash collected + 5 % platform commission recorded as DUE;</li>
 *   <li>recording the settlement fails → DELIVERED stays saved and the error propagates.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DELIVERED — settlement reconciliation instead of money movement")
class DeliveredButUnpaidTest {

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
    @Mock private DeliveryLifecycleUseCase deliveryLifecycleUseCase;
    @Mock private TenantContextHolder tenantContextHolder;

    private final UUID tenantId     = UUID.randomUUID();
    private final UUID deliveryId   = UUID.randomUUID();
    private final UUID profileId    = UUID.randomUUID();
    private final UUID courierActor = UUID.randomUUID();
    private final String otp = "123456";

    private SimpleMeterRegistry meters;
    private Delivery gofpDelivery;
    private final java.util.List<DeliveryStatus> writes = new java.util.concurrent.CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();

        gofpDelivery = new Delivery();
        gofpDelivery.setId(deliveryId);
        gofpDelivery.setFreelancerId(profileId);
        gofpDelivery.setAnnouncementId(UUID.randomUUID()); // no delivery need → digital (ORANGE_MONEY)
        gofpDelivery.setTarif(3000.0);
        gofpDelivery.setDeliveryOtpHash("hash");
        gofpDelivery.setStatus(DeliveryStatus.PICKED_UP);

        var coreDelivery = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery.builder()
                .id(deliveryId).tenantId(tenantId).deliveryPersonId(profileId)
                .status(com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus.PICKED_UP)
                .build();

        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(gofpDeliveryRepository.findById(deliveryId)).thenReturn(Mono.just(gofpDelivery));
        // Only a subscription is a write: record statuses at subscription time.
        when(gofpDeliveryRepository.save(any())).thenAnswer(inv -> {
            Delivery d = inv.getArgument(0);
            return Mono.fromSupplier(() -> { writes.add(d.getStatus()); return d; });
        });
        when(deliveryOtpService.initOtpIfAbsent(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(otpService.verifyOtp(eq(otp), eq("hash"))).thenReturn(true);
        when(deliveryQueryUseCase.findDeliveryById(tenantId, deliveryId)).thenReturn(Mono.just(coreDelivery));
        when(deliveryQueryUseCase.resolveActorIdForDeliveryPerson(tenantId, profileId))
                .thenReturn(Mono.just(courierActor));
        when(missionUseCase.recordCompleted(any(), any(), any())).thenReturn(Mono.empty());
        when(paymentUseCase.record(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        when(custodyTransferUseCase.record(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("digital delivery, client has not paid: DELIVERED saved, call succeeds, UNPAID recorded and counted, nothing anchored")
    void digitalUnpaid_deliveredStays_callSucceeds_unpaidCounted() {
        when(settlements.record(any())).thenAnswer(inv -> Mono.just(settlement(inv.getArgument(0),
                SettlementStatus.UNPAID, CommissionStatus.NONE)));

        StepVerifier.create(service().updateStatus(deliveryId, deliver()))
                .assertNext(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.DELIVERED))
                .verifyComplete();

        assertThat(writes).containsExactly(DeliveryStatus.DELIVERED);
        ArgumentCaptor<RecordSettlementCommand> captor = ArgumentCaptor.forClass(RecordSettlementCommand.class);
        verify(settlements).record(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(SettlementStatus.UNPAID);
        assertThat(captor.getValue().method()).isEqualTo(SettlementMethod.WALLET_TRANSFER);
        assertThat(captor.getValue().payeeUserId()).isEqualTo(courierActor);
        verify(paymentUseCase, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(meters.get("gofp.delivery.payment.failures").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("digital delivery already paid by transfer: nothing counted, payment anchored as DIGITAL_WALLET_TRANSFER")
    void digitalAlreadyPaid_anchored_notCounted() {
        when(settlements.record(any())).thenAnswer(inv -> Mono.just(settlement(inv.getArgument(0),
                SettlementStatus.PAID, CommissionStatus.DUE)));

        StepVerifier.create(service().updateStatus(deliveryId, deliver()))
                .assertNext(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.DELIVERED))
                .verifyComplete();

        verify(paymentUseCase).record(any(), any(), any(), any(), eq("DIGITAL_WALLET_TRANSFER"),
                any(), any(), any());
        assertThat(meters.get("gofp.delivery.payment.failures").counter().count()).isZero();
    }

    @Test
    @DisplayName("CASH delivery: cash collected recorded with the 5% commission DUE, anchored, not counted as unpaid")
    void cashDelivery_commissionRecordedAsDue() {
        gofpDelivery.setAnnouncementId(null);
        UUID needId = UUID.randomUUID();
        gofpDelivery.setDeliveryNeedId(needId);
        when(deliveryNeedRepository.findById(needId))
                .thenReturn(Mono.just(DeliveryNeed.builder().id(needId).paymentMethod("CASH").build()));
        when(settlements.record(any())).thenAnswer(inv -> Mono.just(settlement(inv.getArgument(0),
                SettlementStatus.CASH_COLLECTED, CommissionStatus.DUE)));

        StepVerifier.create(service().updateStatus(deliveryId, deliver()))
                .assertNext(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.DELIVERED))
                .verifyComplete();

        ArgumentCaptor<RecordSettlementCommand> captor = ArgumentCaptor.forClass(RecordSettlementCommand.class);
        verify(settlements).record(captor.capture());
        RecordSettlementCommand cmd = captor.getValue();
        assertThat(cmd.method()).isEqualTo(SettlementMethod.CASH);
        assertThat(cmd.status()).isEqualTo(SettlementStatus.CASH_COLLECTED);
        assertThat(cmd.commissionRate()).isEqualByComparingTo("0.05");
        assertThat(cmd.amount()).isEqualByComparingTo("3000");
        verify(paymentUseCase).record(any(), any(), any(), any(), eq("CASH_COLLECTED_COMMISSION_DUE"),
                any(), any(), any());
        assertThat(meters.get("gofp.delivery.payment.failures").counter().count()).isZero();
    }

    @Test
    @DisplayName("recording the settlement fails after the OTP: DELIVERED stays saved, error propagates, failure counted")
    void settlementFailure_afterDeliveredSaved_statusStays_errorPropagates() {
        when(settlements.record(any())).thenReturn(Mono.error(new IllegalStateException("db down")));

        StepVerifier.create(service().updateStatus(deliveryId, deliver()))
                .expectError(IllegalStateException.class)
                .verify();

        assertThat(writes)
                .as("exactly one write, DELIVERED, before the settlement — nothing reverts it")
                .containsExactly(DeliveryStatus.DELIVERED);
        verify(pushNotificationPort, never()).sendPushNotification(any(), any(), any());
        assertThat(meters.get("gofp.delivery.payment.failures").counter().count()).isEqualTo(1.0);
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private DeliveryStatusApplicationService service() {
        DeliveryStatusApplicationService s = new DeliveryStatusApplicationService(
                gofpDeliveryRepository, relayDepositService, gofpRelayPointRepository,
                pushNotificationPort, deliveryNeedRepository, announcementRepository,
                otpService, deliveryOtpService, settlements,
                custodyTransferUseCase, missionUseCase, paymentUseCase,
                deliveryQueryUseCase, deliveryLifecycleUseCase, tenantContextHolder, meters);
        s.initMetrics();
        return s;
    }

    private WalletSettlement settlement(RecordSettlementCommand c, SettlementStatus status, CommissionStatus cs) {
        return WalletSettlement.builder()
                .id(UUID.randomUUID()).tenantId(c.tenantId()).referenceType(c.referenceType())
                .referenceId(c.referenceId()).payeeUserId(c.payeeUserId()).amount(c.amount())
                .currency(c.currency()).method(c.method()).status(status)
                .commissionRate(c.commissionRate()).commissionAmount(BigDecimal.ZERO)
                .commissionStatus(cs).build();
    }

    private DeliveryStatusUpdateDTO deliver() {
        DeliveryStatusUpdateDTO dto = new DeliveryStatusUpdateDTO();
        dto.setStatus(DeliveryStatus.DELIVERED);
        dto.setConfirmationCode(otp);
        return dto;
    }
}
