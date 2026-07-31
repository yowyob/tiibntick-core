package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CompleteDeliveryCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IRelayHubPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.RelayDepositRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.RelayPointSubscriptionRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayDeposit;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.logistics.RelayDepositStatus;
import com.yowyob.tiibntick.core.inventory.application.port.in.DepositHubPackageUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.in.PickupHubPackageUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordCustodyTransferUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RelayDepositService#markAsRetrieved} — the ordering/compensation fix
 * for the "false failure" bug found in review: a RelayDeposit must only be persisted as
 * RETRIEVED once the irreversible/system-of-record steps (inventory pickup, delivery-core
 * completion) have actually succeeded; everything after that (storage-fee payment, blockchain
 * custody anchoring, evaluation notifications) is best-effort and must never turn an
 * already-successful retrieval into an API error.
 *
 * @author MANFOUO BRAUN
 */
@ExtendWith(MockitoExtension.class)
class RelayDepositServiceTest {

    @Mock private RelayDepositRepository relayDepositRepository;
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private OtpService otpService;
    @Mock private RecordCustodyTransferUseCase custodyTransferUseCase;
    @Mock private NotificationService notificationService;
    @Mock private GofpRelayPointRepository gofpRelayPointRepository;
    @Mock private GofpUserRepository gofpUserRepository;
    @Mock private IWalletUseCase walletUseCase;
    @Mock private IDeliveryNeedRepository deliveryNeedRepository;
    @Mock private RelayPointSubscriptionRepository relayPointSubscriptionRepository;
    @Mock private DepositHubPackageUseCase depositHubPackageUseCase;
    @Mock private PickupHubPackageUseCase pickupHubPackageUseCase;
    @Mock private IRelayHubPort relayHubPort;
    @Mock private DeliveryLifecycleUseCase deliveryLifecycleUseCase;

    private RelayDepositService service;

    private final UUID depositId = UUID.randomUUID();
    private final UUID packetId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID hubId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();
    private final UUID freelancerId = UUID.randomUUID();
    private static final String OTP = "123456";

    @BeforeEach
    void setUp() {
        service = new RelayDepositService(
                relayDepositRepository, deliveryRepository, otpService, custodyTransferUseCase,
                notificationService, gofpRelayPointRepository, gofpUserRepository, walletUseCase,
                deliveryNeedRepository, relayPointSubscriptionRepository, depositHubPackageUseCase,
                pickupHubPackageUseCase, relayHubPort, deliveryLifecycleUseCase);

        // Reactor's switchIfEmpty/then/when require their argument Mono to be non-null even
        // when it's never subscribed — these lenient defaults cover every such fallback
        // lookup so each test only needs to override what it actually cares about.
        lenient().when(deliveryRepository.findByAnnouncementId(any())).thenReturn(Mono.empty());
        lenient().when(gofpRelayPointRepository.findByCoreRelayPointId(any())).thenReturn(Mono.empty());
        lenient().when(gofpRelayPointRepository.findById(any())).thenReturn(Mono.empty());
        lenient().when(gofpUserRepository.findByCoreUserId(any())).thenReturn(Mono.empty());
        lenient().when(gofpUserRepository.findById(any())).thenReturn(Mono.empty());
        lenient().when(deliveryNeedRepository.findById(any())).thenReturn(Mono.empty());
        lenient().when(relayPointSubscriptionRepository.findByRelayPointId(any())).thenReturn(Mono.empty());
        lenient().when(relayDepositRepository.save(any(RelayDeposit.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        lenient().when(custodyTransferUseCase.record(any())).thenReturn(Mono.just("0xTX"));
        lenient().when(pickupHubPackageUseCase.pickupPackage(anyString(), any())).thenReturn(Mono.empty());
        lenient().when(deliveryLifecycleUseCase.completeDelivery(any(CompleteDeliveryCommand.class)))
                .thenReturn(Mono.just(mock(com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery.class)));
        lenient().when(relayHubPort.decrementOccupancy(any(), any()))
                .thenReturn(Mono.just(mock(com.yowyob.tiibntick.core.geo.domain.model.RelayHub.class)));
    }

    private RelayDeposit deposit() {
        return RelayDeposit.builder()
                .id(depositId)
                .packetId(packetId)
                .clientId(clientId)
                .relayPointId(hubId)
                .storageFee(500.0)
                .currency("XAF")
                .status(RelayDepositStatus.DELIVERED)
                .build();
    }

    private Delivery gofpDelivery() {
        Delivery d = new Delivery();
        d.setId(deliveryId);
        d.setFreelancerId(freelancerId);
        d.setDeliveryOtpHash("hash");
        return d;
    }

    private void givenValidDepositAndOtp() {
        when(relayDepositRepository.findById(depositId)).thenReturn(Mono.just(deposit()));
        when(deliveryRepository.findByDeliveryNeedId(packetId)).thenReturn(Mono.just(gofpDelivery()));
        when(otpService.verifyOtp(OTP, "hash")).thenReturn(true);
    }

    @Test
    void markAsRetrieved_happyPath_persistsRetrievedAndCompletesDeliveryCore() {
        givenValidDepositAndOtp();

        StepVerifier.create(service.markAsRetrieved(depositId, OTP))
                .expectNextMatches(saved -> saved.getStatus() == RelayDepositStatus.RETRIEVED
                        && saved.getRetrievedAt() != null)
                .verifyComplete();

        verify(deliveryLifecycleUseCase).completeDelivery(any(CompleteDeliveryCommand.class));
        verify(relayDepositRepository).save(any(RelayDeposit.class));
    }

    @Test
    void markAsRetrieved_inventoryPickupFails_propagatesError() {
        givenValidDepositAndOtp();
        when(pickupHubPackageUseCase.pickupPackage(anyString(), any()))
                .thenReturn(Mono.error(new IllegalStateException("inventory down")));

        StepVerifier.create(service.markAsRetrieved(depositId, OTP))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void markAsRetrieved_deliveryCoreCompletionFails_propagatesError() {
        givenValidDepositAndOtp();
        when(deliveryLifecycleUseCase.completeDelivery(any(CompleteDeliveryCommand.class)))
                .thenReturn(Mono.error(new IllegalStateException("delivery-core rejected transition")));

        StepVerifier.create(service.markAsRetrieved(depositId, OTP))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void markAsRetrieved_geoHubMissing_softFailsAndStillPersistsRetrieved() {
        givenValidDepositAndOtp();
        when(relayHubPort.decrementOccupancy(any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("RelayHub not found in geo-core: " + hubId)));

        StepVerifier.create(service.markAsRetrieved(depositId, OTP))
                .expectNextMatches(saved -> saved.getStatus() == RelayDepositStatus.RETRIEVED)
                .verifyComplete();
    }

    /**
     * The exact bug from the review: storage-fee collection fails (relay point owner missing)
     * AFTER the parcel is already durably marked RETRIEVED. The retrieval must still succeed
     * from the caller's point of view — a wallet/reconciliation issue is not the courier's problem.
     */
    @Test
    void markAsRetrieved_postRetrievalPaymentFails_stillReturnsRetrievedDeposit() {
        givenValidDepositAndOtp();
        GofpRelayPoint rpNoOwner = GofpRelayPoint.builder()
                .id(UUID.randomUUID()).coreRelayPointId(hubId).coreFreelancerId(null).build();
        when(gofpRelayPointRepository.findByCoreRelayPointId(hubId)).thenReturn(Mono.just(rpNoOwner));

        StepVerifier.create(service.markAsRetrieved(depositId, OTP))
                .expectNextMatches(saved -> saved.getStatus() == RelayDepositStatus.RETRIEVED)
                .verifyComplete();

        verify(relayDepositRepository).save(any(RelayDeposit.class));
        verify(walletUseCase, org.mockito.Mockito.never()).debitWallet(any());
        verify(walletUseCase, org.mockito.Mockito.never()).splitMissionRevenue(any());
    }

    @Test
    void markAsRetrieved_blockchainAnchoringFails_stillReturnsRetrievedDeposit() {
        givenValidDepositAndOtp();
        when(custodyTransferUseCase.record(any()))
                .thenReturn(Mono.error(new RuntimeException("Hyperledger Fabric unreachable")));

        StepVerifier.create(service.markAsRetrieved(depositId, OTP))
                .expectNextMatches(saved -> saved.getStatus() == RelayDepositStatus.RETRIEVED)
                .verifyComplete();
    }

    @Test
    void markAsRetrieved_missingOtp_rejectsBeforeAnySideEffect() {
        when(relayDepositRepository.findById(depositId)).thenReturn(Mono.just(deposit()));

        StepVerifier.create(service.markAsRetrieved(depositId, ""))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(pickupHubPackageUseCase, org.mockito.Mockito.never()).pickupPackage(anyString(), any());
    }

    @Test
    void markAsRetrieved_wrongOtp_rejectsBeforeAnySideEffect() {
        when(relayDepositRepository.findById(depositId)).thenReturn(Mono.just(deposit()));
        when(deliveryRepository.findByDeliveryNeedId(packetId)).thenReturn(Mono.just(gofpDelivery()));
        when(otpService.verifyOtp("000000", "hash")).thenReturn(false);

        StepVerifier.create(service.markAsRetrieved(depositId, "000000"))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(pickupHubPackageUseCase, org.mockito.Mockito.never()).pickupPackage(anyString(), any());
    }
}
