package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.SplitMissionRevenueCommand;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentSplitResult;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryStatusUpdateDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordCustodyTransferUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordMissionUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordPaymentUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves that processDeliveryPayment resolves the kernel actorId from the delivery-person
 * profile ID before building SplitMissionRevenueCommand / DebitWalletCommand.
 *
 * <p>Root cause (Lot C-9): delivery.freelancerId = tnt_delivery_persons.id (profile UUID),
 * while wallets are keyed on tnt_delivery_persons.actor_id (kernel actorId). Passing the
 * wrong ID as freelancerOrgId produces WalletNotFoundException.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryStatusApplicationService — actorId resolution for payment")
class DeliveryStatusPaymentActorResolutionTest {

    @Mock private DeliveryRepository gofpDeliveryRepository;
    @Mock private RelayDepositService relayDepositService;
    @Mock private GofpRelayPointRepository gofpRelayPointRepository;
    @Mock private PushNotificationPort pushNotificationPort;
    @Mock private IDeliveryNeedRepository deliveryNeedRepository;
    @Mock private com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository announcementRepository;
    @Mock private OtpService otpService;
    @Mock private DeliveryOtpService deliveryOtpService;
    @Mock private IWalletUseCase walletUseCase;
    @Mock private RecordCustodyTransferUseCase custodyTransferUseCase;
    @Mock private RecordMissionUseCase missionUseCase;
    @Mock private RecordPaymentUseCase paymentUseCase;
    @Mock private DeliveryQueryUseCase deliveryQueryUseCase;
    @Mock private DeliveryLifecycleUseCase deliveryLifecycleUseCase;
    @Mock private TenantContextHolder tenantContextHolder;

    private DeliveryStatusApplicationService service;

    private final UUID tenantId            = UUID.randomUUID();
    private final UUID deliveryId          = UUID.randomUUID();
    private final UUID announcementId      = UUID.randomUUID();
    /** Profile ID — tnt_delivery_persons.id, also gofp freelancerId */
    private final UUID freelancerProfileId = UUID.randomUUID();
    /** Kernel actorId — tnt_delivery_persons.actor_id, wallet owner */
    private final UUID freelancerActorId   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DeliveryStatusApplicationService(
                gofpDeliveryRepository, relayDepositService, gofpRelayPointRepository,
                pushNotificationPort, deliveryNeedRepository, announcementRepository,
                otpService, deliveryOtpService,
                walletUseCase,
                custodyTransferUseCase, missionUseCase, paymentUseCase,
                deliveryQueryUseCase, deliveryLifecycleUseCase,
                tenantContextHolder);
    }

    @Test
    @DisplayName("SplitMissionRevenueCommand.freelancerOrgId == actorId (not the profile freelancerId)")
    void updateStatus_delivered_splitCommandCarriesResolvedActorId() {
        String otpHash = "hashed-otp";
        String otpClear = "654321";

        // GOFP delivery — @Data, use setters
        com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery gofpDelivery =
                new com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery();
        gofpDelivery.setId(deliveryId);
        gofpDelivery.setFreelancerId(freelancerProfileId);
        gofpDelivery.setAnnouncementId(announcementId);
        gofpDelivery.setTarif(5000.0);
        gofpDelivery.setDeliveryOtpHash(otpHash);
        gofpDelivery.setStatus(DeliveryStatus.IN_TRANSIT);

        // Core delivery — uses @Builder
        com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery coreDelivery =
                com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery.builder()
                        .id(deliveryId)
                        .tenantId(tenantId)
                        .deliveryPersonId(freelancerProfileId)
                        .status(com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus.IN_TRANSIT)
                        .build();

        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(gofpDeliveryRepository.findById(deliveryId)).thenReturn(Mono.just(gofpDelivery));
        when(deliveryOtpService.initOtpIfAbsent(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(otpService.verifyOtp(eq(otpClear), eq(otpHash))).thenReturn(true);
        when(deliveryQueryUseCase.findDeliveryById(tenantId, deliveryId)).thenReturn(Mono.just(coreDelivery));
        when(gofpDeliveryRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // The key: profile ID → kernel actorId
        when(deliveryQueryUseCase.resolveActorIdForDeliveryPerson(tenantId, freelancerProfileId))
                .thenReturn(Mono.just(freelancerActorId));

        // ORANGE_MONEY path → splitMissionRevenue (no deliveryNeedId → paymentMethod = ORANGE_MONEY)
        when(walletUseCase.splitMissionRevenue(any())).thenReturn(Mono.just(
                new PaymentSplitResult(UUID.randomUUID(), deliveryId.toString(),
                        BigDecimal.valueOf(5000), "XAF",
                        BigDecimal.valueOf(250), BigDecimal.valueOf(4750),
                        BigDecimal.ZERO, null, "COMPLETED")));

        // Blockchain — best-effort, not under test here
        when(missionUseCase.recordCompleted(any(), any(), any())).thenReturn(Mono.empty());
        when(paymentUseCase.record(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());
        when(custodyTransferUseCase.record(any())).thenReturn(Mono.empty());

        DeliveryStatusUpdateDTO dto = new DeliveryStatusUpdateDTO();
        dto.setStatus(DeliveryStatus.DELIVERED);
        dto.setConfirmationCode(otpClear);

        StepVerifier.create(service.updateStatus(deliveryId, dto))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<SplitMissionRevenueCommand> captor =
                ArgumentCaptor.forClass(SplitMissionRevenueCommand.class);
        verify(walletUseCase).splitMissionRevenue(captor.capture());

        SplitMissionRevenueCommand cmd = captor.getValue();
        assertThat(cmd.freelancerOrgId())
                .as("SplitMissionRevenueCommand.freelancerOrgId must be the resolved actorId, "
                        + "not the profile freelancerId — wallets are keyed on actor_id")
                .isEqualTo(freelancerActorId.toString());
        assertThat(cmd.freelancerOrgId())
                .as("Must differ from the profile UUID that was the input")
                .isNotEqualTo(freelancerProfileId.toString());
        assertThat(cmd.missionId()).isEqualTo(deliveryId.toString());
        assertThat(cmd.tenantId()).isEqualTo(tenantId);
    }
}
