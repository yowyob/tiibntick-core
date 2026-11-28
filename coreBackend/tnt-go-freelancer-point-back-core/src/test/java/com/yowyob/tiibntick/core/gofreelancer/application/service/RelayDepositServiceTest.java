package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ISettlementUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CompleteDeliveryCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IRelayHubPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.RelayDepositRepository;
import com.yowyob.tiibntick.core.inventory.application.port.in.DepositHubPackageCommand;
import com.yowyob.tiibntick.core.inventory.application.port.in.FindHubPackagesUseCase;
import com.yowyob.tiibntick.core.inventory.domain.model.HubPackageEntry;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.RelayPointSubscriptionRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RelayDepositService}. The deposit is the inventory hub package entry
 * (no Go-Freelancer table any more). {@link RelayDepositService#markAsRetrieved} — the
 * ordering/compensation fix for the "false failure" bug found in review: a deposit is only
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
    @Mock private ISettlementUseCase settlements;
    @Mock private IDeliveryNeedRepository deliveryNeedRepository;
    @Mock private RelayPointSubscriptionRepository relayPointSubscriptionRepository;
    @Mock private DepositHubPackageUseCase depositHubPackageUseCase;
    @Mock private PickupHubPackageUseCase pickupHubPackageUseCase;
    @Mock private FindHubPackagesUseCase findHubPackagesUseCase;
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
                notificationService, gofpRelayPointRepository, gofpUserRepository, settlements,
                deliveryNeedRepository, relayPointSubscriptionRepository, depositHubPackageUseCase,
                pickupHubPackageUseCase, findHubPackagesUseCase, relayHubPort, deliveryLifecycleUseCase);

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
        lenient().when(findHubPackagesUseCase.findEntry(depositId))
                .thenAnswer(inv -> Mono.justOrEmpty(stored.get()));
        lenient().when(custodyTransferUseCase.record(any())).thenReturn(Mono.just("0xTX"));
        lenient().when(pickupHubPackageUseCase.pickupPackage(anyString(), any())).thenAnswer(inv -> Mono.fromRunnable(
                () -> stored.set(stored.get().markPickedUp(inv.getArgument(1)))));
        lenient().when(deliveryLifecycleUseCase.completeDelivery(any(CompleteDeliveryCommand.class)))
                .thenReturn(Mono.just(mock(com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery.class)));
        lenient().when(relayHubPort.decrementOccupancy(any(), any()))
                .thenReturn(Mono.just(mock(com.yowyob.tiibntick.core.geo.domain.model.RelayHub.class)));
    }

    /** The inventory entry the deposit is (picked up by the stubbed pickup). */
    private final AtomicReference<HubPackageEntry> stored = new AtomicReference<>();

    private HubPackageEntry entry() {
        return HubPackageEntry.rehydrate(depositId, UUID.randomUUID(), hubId, packetId, "TRK-" + packetId, null,
                Instant.now(), null, null, null, null, false, clientId, new BigDecimal("500.00"), null, "XAF");
    }

    private Delivery gofpDelivery() {
        Delivery d = new Delivery();
        d.setId(deliveryId);
        com.yowyob.tiibntick.core.gofreelancer.TestDeliveries.driver(d, freelancerId);
        d.setDeliveryOtpHash("hash");
        return d;
    }

    private void givenValidDepositAndOtp() {
        stored.set(entry());
        when(deliveryRepository.findByDeliveryNeedId(packetId)).thenReturn(Mono.just(gofpDelivery()));
        when(otpService.verifyOtp(OTP, "hash")).thenReturn(true);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("lot L1: a deposit keyed on a parcel id (no need, no announcement with that id) is retrieved through the parcel")
    void markAsRetrieved_depositKeyedOnTheParcel_findsTheDelivery() {
        stored.set(entry());
        when(deliveryRepository.findByDeliveryNeedId(packetId)).thenReturn(Mono.empty());
        when(deliveryRepository.findByParcelId(packetId)).thenReturn(Mono.just(gofpDelivery()));
        when(otpService.verifyOtp(OTP, "hash")).thenReturn(true);

        StepVerifier.create(service.markAsRetrieved(depositId, OTP))
                .expectNextMatches(saved -> saved.getStatus() == RelayDepositStatus.RETRIEVED)
                .verifyComplete();
        verify(findHubPackagesUseCase, org.mockito.Mockito.atLeastOnce()).findEntry(depositId);
    }

    @Test
    void markAsRetrieved_happyPath_persistsRetrievedAndCompletesDeliveryCore() {
        givenValidDepositAndOtp();

        StepVerifier.create(service.markAsRetrieved(depositId, OTP))
                .expectNextMatches(saved -> saved.getStatus() == RelayDepositStatus.RETRIEVED
                        && saved.getRetrievedAt() != null && saved.getId().equals(depositId))
                .verifyComplete();

        verify(deliveryLifecycleUseCase).completeDelivery(any(CompleteDeliveryCommand.class));
        verify(pickupHubPackageUseCase).pickupPackage("TRK-" + packetId.toString().toUpperCase(), clientId);
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

        assertThat(stored.get().isPickedUp()).isTrue();
        verify(settlements, org.mockito.Mockito.never()).record(any());
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
        stored.set(entry());

        StepVerifier.create(service.markAsRetrieved(depositId, ""))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(pickupHubPackageUseCase, org.mockito.Mockito.never()).pickupPackage(anyString(), any());
    }

    @Test
    void markAsRetrieved_wrongOtp_rejectsBeforeAnySideEffect() {
        stored.set(entry());
        when(deliveryRepository.findByDeliveryNeedId(packetId)).thenReturn(Mono.just(gofpDelivery()));
        when(otpService.verifyOtp("000000", "hash")).thenReturn(false);

        StepVerifier.create(service.markAsRetrieved(depositId, "000000"))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(pickupHubPackageUseCase, org.mockito.Mockito.never()).pickupPackage(anyString(), any());
    }

    @Test
    void createRelayDeposit_depositsTheEntryWithOwnerAndFee_andReturnsItAsTheDeposit() {
        GofpRelayPoint rp = GofpRelayPoint.builder().id(UUID.randomUUID()).coreRelayPointId(hubId).build();
        when(gofpRelayPointRepository.findByCoreRelayPointId(hubId)).thenReturn(Mono.just(rp));
        when(gofpRelayPointRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(relayHubPort.incrementOccupancy(any(), any()))
                .thenReturn(Mono.just(mock(com.yowyob.tiibntick.core.geo.domain.model.RelayHub.class)));
        UUID driver = UUID.randomUUID();
        when(depositHubPackageUseCase.depositPackage(any())).thenAnswer(inv -> {
            DepositHubPackageCommand c = inv.getArgument(0);
            return Mono.just(HubPackageEntry.deposit(c.tenantId(), c.hubId(), c.packageId(), c.trackingCode(),
                    c.storageLocation(), c.depositedByActorId(), c.recipientPhone(), c.ownerActorId(),
                    c.storageFee(), c.feeCurrency()));
        });

        StepVerifier.create(service.createRelayDeposit(packetId, clientId, hubId, 750.0, driver))
                .assertNext(d -> {
                    assertThat(d.getPacketId()).isEqualTo(packetId);
                    assertThat(d.getClientId()).isEqualTo(clientId);
                    assertThat(d.getRelayPointId()).isEqualTo(hubId);
                    assertThat(d.getStorageFee()).isEqualTo(750.0);
                    assertThat(d.getCurrency()).isEqualTo("XAF");
                    assertThat(d.getStatus()).isEqualTo(RelayDepositStatus.DELIVERED);
                    assertThat(d.getRetrievedAt()).isNull();
                })
                .verifyComplete();

        org.mockito.ArgumentCaptor<DepositHubPackageCommand> cmd =
                org.mockito.ArgumentCaptor.forClass(DepositHubPackageCommand.class);
        verify(depositHubPackageUseCase).depositPackage(cmd.capture());
        assertThat(cmd.getValue().ownerActorId()).isEqualTo(clientId);
        assertThat(cmd.getValue().depositedByActorId()).isEqualTo(driver);
        assertThat(cmd.getValue().storageFee()).isEqualByComparingTo("750");
        assertThat(rp.getTotalDeposits()).isEqualTo(1);
    }

    @Test
    void retrievalRecordsTheStorageFeeOfTheEntry() {
        givenValidDepositAndOtp();
        UUID owner = UUID.randomUUID();
        GofpRelayPoint rp = GofpRelayPoint.builder().id(UUID.randomUUID()).coreRelayPointId(hubId)
                .coreFreelancerId(owner).build();
        when(gofpRelayPointRepository.findByCoreRelayPointId(hubId)).thenReturn(Mono.just(rp));
        when(settlements.record(any())).thenReturn(Mono.empty());
        when(notificationService.sendEvaluationRequest(any(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(service.markAsRetrieved(depositId, OTP)).expectNextCount(1).verifyComplete();

        org.mockito.ArgumentCaptor<com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.RecordSettlementCommand>
                st = org.mockito.ArgumentCaptor.forClass(
                        com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.RecordSettlementCommand.class);
        verify(settlements).record(st.capture());
        assertThat(st.getValue().referenceId()).isEqualTo(depositId);
        assertThat(st.getValue().amount()).isEqualByComparingTo("500");
        assertThat(st.getValue().payeeUserId()).isEqualTo(owner);
    }

    @Test
    void depositsAreReadFromTheInventoryAsViews() {
        RelayDepositRepository reader = new com.yowyob.tiibntick.core.gofreelancer.adapter.out.inventory
                .InventoryRelayDepositReader(findHubPackagesUseCase);
        HubPackageEntry picked = entry().markPickedUp(clientId);
        when(findHubPackagesUseCase.findEntriesOfHub(hubId)).thenReturn(reactor.core.publisher.Flux.just(picked));
        when(findHubPackagesUseCase.findEntriesOfOwner(clientId)).thenReturn(reactor.core.publisher.Flux.just(picked));

        StepVerifier.create(reader.findByRelayPointId(hubId))
                .assertNext(d -> {
                    assertThat(d.getId()).isEqualTo(depositId);
                    assertThat(d.getStatus()).isEqualTo(RelayDepositStatus.RETRIEVED);
                    assertThat(d.getRetrievedAt()).isNotNull();
                    assertThat(d.isNew()).isFalse();
                }).verifyComplete();
        StepVerifier.create(reader.findByClientId(clientId)).expectNextCount(1).verifyComplete();
        StepVerifier.create(reader.findByClientId(null)).verifyComplete();
    }
}
