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

import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryEventPublisher;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryPersonRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryProofAnchorPort;
import com.yowyob.tiibntick.core.delivery.application.port.out.EtaComputationPort;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryLifecycleService;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.MissionStatusChangedEvent;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryNotFoundException;
import com.yowyob.tiibntick.core.delivery.domain.exception.InvalidDeliveryStateTransitionException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.EtaEstimate;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery.DeliveryLifecyclePortAdapter;
import com.yowyob.tiibntick.core.route.application.port.in.IUpdateEtaUseCase;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Chains the REAL tnt-delivery-core lifecycle service (state machine, aggregate, event emission) with
 * the REAL gofp status service through the REAL {@link DeliveryLifecyclePortAdapter}. Persistence is
 * in memory; Kafka/trust/wallet are doubles. Proves the convergence end to end: the core decides, the
 * gofp row follows, and the mission events every other module reacts to are actually emitted.
 *
 * <p>Not covered here (needs the full Spring context + Kafka/PostgreSQL): the AOP permission guards of
 * the core use cases and the outbox relay; those have their own tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("gofp <-> delivery-core convergence (real services, in-memory persistence)")
class DeliveryCoreConvergenceIntegrationTest {

    private static final String PICKUP_CODE = "111111";
    private static final String DELIVERY_CODE = "222222";

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
    @Mock private TenantContextHolder tenantContextHolder;
    @Mock private DeliveryRepository gofpRepository;
    @Mock private com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository coreRepository;
    @Mock private DeliveryPersonRepository deliveryPersonRepository;
    @Mock private EtaComputationPort etaComputationPort;
    @Mock private DeliveryProofAnchorPort proofAnchorPort;
    @Mock private IUpdateEtaUseCase updateEtaUseCase;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID driver = UUID.randomUUID();
    private final Map<UUID, com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery> coreStore = new ConcurrentHashMap<>();
    private final Map<UUID, Delivery> gofpStore = new ConcurrentHashMap<>();
    private final List<String> publishedStatuses = new CopyOnWriteArrayList<>();

    private com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery coreDelivery;
    private UUID deliveryId;
    private DeliveryStatusApplicationService statusService;
    private DeliveryApplicationService deliveryService;
    private IDeliveryLifecyclePort port;

    @BeforeEach
    void setUp() {
        // ── real core: a delivery as the core creates it at announcement response selection ──
        Parcel parcel = Parcel.create(new PackageSpecification(1.5, 20, 15, 10, false, false, "Test"));
        coreDelivery = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery.create(
                tenantId, null, UUID.randomUUID(), parcel,
                new DeliveryAddress(null, "Mokolo", "Mokolo", "Yaoundé", "CM", new GeoCoordinates(3.87, 11.52)),
                new DeliveryAddress(null, "Bastos", "Bastos", "Yaoundé", "CM", new GeoCoordinates(3.88, 11.53)),
                new RecipientInfo("Test", "+237690000001", null), DeliveryUrgency.STANDARD, null, null);
        coreDelivery.assignDeliveryPerson(driver, null, 5.0, null);
        coreDelivery.clearDomainEvents();
        deliveryId = coreDelivery.getId();
        coreStore.put(deliveryId, coreDelivery);

        when(coreRepository.findById(eq(tenantId), any())).thenAnswer(inv ->
                Mono.justOrEmpty(coreStore.get(inv.<UUID>getArgument(1))));
        when(coreRepository.save(any())).thenAnswer(inv -> {
            var d = inv.<com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery>getArgument(0);
            coreStore.put(d.getId(), d);
            return Mono.just(d);
        });
        DeliveryEventPublisher publisher = new DeliveryEventPublisher() {
            @Override public Mono<Void> publish(DeliveryDomainEvent event) { return publishAll(List.of(event)); }
            @Override public Mono<Void> publishAll(List<DeliveryDomainEvent> events) {
                events.stream().filter(MissionStatusChangedEvent.class::isInstance)
                        .map(e -> ((MissionStatusChangedEvent) e).newStatus()).forEach(publishedStatuses::add);
                return Mono.empty();
            }
        };
        when(etaComputationPort.computeInitial(any(), any(), anyDouble()))
                .thenReturn(Mono.just(EtaEstimate.of(Instant.now().plusSeconds(900), 2.0, 15)));
        when(deliveryPersonRepository.findByActorId(any(), any())).thenReturn(Mono.empty());
        when(deliveryPersonRepository.findById(any(), any())).thenReturn(Mono.empty());
        when(proofAnchorPort.anchor(any())).thenReturn(Mono.empty());
        when(updateEtaUseCase.computeInitialEta(any(), anyDouble(), anyDouble())).thenReturn(Mono.empty());
        DeliveryLifecycleService coreLifecycle = new DeliveryLifecycleService(
                coreRepository, deliveryPersonRepository, etaComputationPort, publisher, proofAnchorPort, updateEtaUseCase);
        when(deliveryQueryUseCase.findDeliveryById(eq(tenantId), any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(1);
            var d = coreStore.get(id);
            return d != null ? Mono.just(d) : Mono.error(new DeliveryNotFoundException(id));
        });
        port = new DeliveryLifecyclePortAdapter(coreLifecycle, deliveryQueryUseCase);

        // ── real gofp: the mirror row with the same id ──
        Delivery mirror = new Delivery();
        mirror.setId(deliveryId);
        mirror.setFreelancerId(driver);
        mirror.setStatus(DeliveryStatus.CREATED);
        mirror.setPickupOtpHash("pickup-hash");
        mirror.setDeliveryOtpHash("delivery-hash");
        gofpStore.put(deliveryId, mirror);
        when(gofpRepository.findById(any(UUID.class))).thenAnswer(inv -> Mono.justOrEmpty(gofpStore.get(inv.<UUID>getArgument(0))));
        when(gofpRepository.save(any())).thenAnswer(inv -> {
            Delivery d = inv.getArgument(0);
            gofpStore.put(d.getId(), d);
            return Mono.just(d);
        });
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(deliveryOtpService.initOtpIfAbsent(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(otpService.verifyOtp(PICKUP_CODE, "pickup-hash")).thenReturn(true);
        when(otpService.verifyOtp(DELIVERY_CODE, "delivery-hash")).thenReturn(true);
        when(custodyTransferUseCase.record(any())).thenReturn(Mono.empty());
        when(missionUseCase.recordCreated(any(), any(), any(), anyInt())).thenReturn(Mono.empty());
        when(missionUseCase.recordCompleted(any(), any(), any())).thenReturn(Mono.empty());
        when(deliveryQueryUseCase.resolveActorIdForDeliveryPerson(any(), any())).thenReturn(Mono.just(driver));
        when(settlements.record(any())).thenReturn(Mono.empty());

        statusService = new DeliveryStatusApplicationService(
                gofpRepository, relayDepositService, gofpRelayPointRepository, pushNotificationPort,
                deliveryNeedRepository, announcementRepository, otpService, deliveryOtpService, settlements,
                custodyTransferUseCase, missionUseCase, paymentUseCase, deliveryQueryUseCase, port,
                tenantContextHolder, new SimpleMeterRegistry());
        statusService.initMetrics();
        deliveryService = new DeliveryApplicationService(
                gofpRepository, deliveryNeedRepository, announcementRepository,
                org.mockito.Mockito.mock(com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.AddressReactiveRepository.class),
                org.mockito.Mockito.mock(com.yowyob.tiibntick.core.realtime.application.port.in.IGetPresenceUseCase.class),
                org.mockito.Mockito.mock(com.yowyob.tiibntick.core.gofreelancer.application.port.out.CachePort.class),
                tenantContextHolder, port,
                org.mockito.Mockito.mock(com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository.class));
    }

    private static DeliveryStatusUpdateDTO dto(DeliveryStatus status, String code) {
        DeliveryStatusUpdateDTO d = new DeliveryStatusUpdateDTO();
        d.setStatus(status);
        d.setConfirmationCode(code);
        return d;
    }

    private String coreStatus() {
        return coreStore.get(deliveryId).getStatus().name();
    }

    @Test
    @DisplayName("nominal run CREATED -> PICKED_UP -> IN_TRANSIT -> DELIVERED: core and mirror agree at each step and every transition is published")
    void nominalRun() {
        StepVerifier.create(statusService.updateStatus(deliveryId, dto(DeliveryStatus.PICKED_UP, PICKUP_CODE)))
                .assertNext(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP)).verifyComplete();
        assertThat(coreStatus()).isEqualTo("PICKED_UP");
        assertThat(gofpStore.get(deliveryId).getActualPickupTime()).isNotNull();

        StepVerifier.create(statusService.updateStatus(deliveryId, dto(DeliveryStatus.IN_TRANSIT, null)))
                .assertNext(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT)).verifyComplete();
        assertThat(coreStatus()).isEqualTo("IN_TRANSIT");

        StepVerifier.create(statusService.updateStatus(deliveryId, dto(DeliveryStatus.DELIVERED, DELIVERY_CODE)))
                .assertNext(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.DELIVERED)).verifyComplete();
        assertThat(coreStatus()).isEqualTo("DELIVERED");
        assertThat(gofpStore.get(deliveryId).getActualDeliveryTime()).isNotNull();

        assertThat(publishedStatuses).containsExactly("PICKED_UP", "IN_TRANSIT", "DELIVERED");
    }

    @Test
    @DisplayName("one tap on DELIVERED right after pickup: the transit is started in the core first, all three transitions are published")
    void deliveredRightAfterPickup() {
        statusService.updateStatus(deliveryId, dto(DeliveryStatus.PICKED_UP, PICKUP_CODE)).block();
        StepVerifier.create(statusService.updateStatus(deliveryId, dto(DeliveryStatus.DELIVERED, DELIVERY_CODE)))
                .assertNext(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.DELIVERED)).verifyComplete();

        assertThat(coreStatus()).isEqualTo("DELIVERED");
        assertThat(publishedStatuses).containsExactly("PICKED_UP", "IN_TRANSIT", "DELIVERED");
    }

    @Test
    @DisplayName("an impossible move (DELIVERED straight from CREATED) is refused by the core: 409 material, mirror untouched, nothing published")
    void impossibleMove_leavesBothSidesUntouched() {
        StepVerifier.create(statusService.updateStatus(deliveryId, dto(DeliveryStatus.DELIVERED, DELIVERY_CODE)))
                .expectError(InvalidDeliveryStateTransitionException.class).verify();

        assertThat(coreStatus()).isEqualTo("CREATED");
        assertThat(gofpStore.get(deliveryId).getStatus()).isEqualTo(DeliveryStatus.CREATED);
        assertThat(publishedStatuses).isEmpty();
    }

    @Test
    @DisplayName("a delivery picked up with the wrong code never reaches the core")
    void wrongPickupCode() {
        StepVerifier.create(statusService.updateStatus(deliveryId, dto(DeliveryStatus.PICKED_UP, "000000")))
                .expectError(IllegalArgumentException.class).verify();
        assertThat(coreStatus()).isEqualTo("CREATED");
        assertThat(publishedStatuses).isEmpty();
    }

    @Test
    @DisplayName("cancel before pickup goes through the core and the mirror follows")
    void cancelBeforePickup() {
        StepVerifier.create(deliveryService.cancelDelivery(deliveryId, UUID.randomUUID(), "changed my mind"))
                .assertNext(r -> assertThat(r.getStatus()).isEqualTo(DeliveryStatus.CANCELLED)).verifyComplete();
        assertThat(coreStatus()).isEqualTo("CANCELLED");
        assertThat(gofpStore.get(deliveryId).getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        assertThat(publishedStatuses).containsExactly("CANCELLED");
    }

    @Test
    @DisplayName("cancel while the parcel is moving is refused by the core (a dispute is the way out), mirror untouched")
    void cancelInTransit_isRefused() {
        statusService.updateStatus(deliveryId, dto(DeliveryStatus.PICKED_UP, PICKUP_CODE)).block();
        statusService.updateStatus(deliveryId, dto(DeliveryStatus.IN_TRANSIT, null)).block();

        StepVerifier.create(deliveryService.cancelDelivery(deliveryId, UUID.randomUUID(), "too late"))
                .expectError(InvalidDeliveryStateTransitionException.class).verify();
        assertThat(coreStatus()).isEqualTo("IN_TRANSIT");
        assertThat(gofpStore.get(deliveryId).getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
    }

    @Test
    @DisplayName("an incident pauses the mission inside the core: the projector brings the mirror along, and the delivery cannot be completed meanwhile")
    void incidentPause_isProjected_andBlocksCompletion() {
        statusService.updateStatus(deliveryId, dto(DeliveryStatus.PICKED_UP, PICKUP_CODE)).block();
        statusService.updateStatus(deliveryId, dto(DeliveryStatus.IN_TRANSIT, null)).block();
        coreStore.get(deliveryId).pauseForIncident(UUID.randomUUID());
        CoreDeliveryStatusProjector projector = new CoreDeliveryStatusProjector(gofpRepository);

        StepVerifier.create(projector.project(deliveryId, "PAUSED_BY_INCIDENT", Instant.now()))
                .expectNext(true).verifyComplete();
        assertThat(gofpStore.get(deliveryId).getStatus()).isEqualTo(DeliveryStatus.PAUSED_BY_INCIDENT);

        StepVerifier.create(statusService.updateStatus(deliveryId, dto(DeliveryStatus.DELIVERED, DELIVERY_CODE)))
                .expectError(InvalidDeliveryStateTransitionException.class).verify();
        assertThat(gofpStore.get(deliveryId).getStatus()).isEqualTo(DeliveryStatus.PAUSED_BY_INCIDENT);
    }
}
