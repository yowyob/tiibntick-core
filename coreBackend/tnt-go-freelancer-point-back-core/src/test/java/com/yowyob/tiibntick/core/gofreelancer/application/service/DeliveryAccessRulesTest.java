package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ISettlementUseCase;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletSettlement;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordCustodyTransferUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordMissionUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordPaymentUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Lot C-45 — the rules behind {@code GofpDeliveryController}: a delivery is read only by its
 * client, its assigned delivery person or staff; its price is changed only by staff, and never
 * once the payment has started.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Delivery access rules — reads limited to the parties, price protected")
class DeliveryAccessRulesTest {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID DELIVERY = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000045");
    static final UUID NEED = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000045");
    static final UUID PROFILE = UUID.fromString("cccccccc-0000-0000-0000-000000000045");
    static final UUID CLIENT = UUID.fromString("11111111-0000-0000-0000-000000000045");
    static final UUID COURIER = UUID.fromString("22222222-0000-0000-0000-000000000045");
    static final UUID STRANGER = UUID.fromString("33333333-0000-0000-0000-000000000045");

    @Mock DeliveryRepository deliveryRepository;
    @Mock RelayDepositService relayDepositService;
    @Mock GofpRelayPointRepository gofpRelayPointRepository;
    @Mock PushNotificationPort pushNotificationPort;
    @Mock IDeliveryNeedRepository deliveryNeedRepository;
    @Mock IAnnouncementRepository announcementRepository;
    @Mock OtpService otpService;
    @Mock DeliveryOtpService deliveryOtpService;
    @Mock ISettlementUseCase settlements;
    @Mock RecordCustodyTransferUseCase custodyTransferUseCase;
    @Mock RecordMissionUseCase missionUseCase;
    @Mock RecordPaymentUseCase paymentUseCase;
    @Mock DeliveryQueryUseCase deliveryQueryUseCase;
    @Mock IDeliveryLifecyclePort lifecycle;
    @Mock TenantContextHolder tenantContextHolder;

    private DeliveryStatusApplicationService rules;
    private Delivery delivery;

    @BeforeEach
    void setUp() {
        rules = new DeliveryStatusApplicationService(deliveryRepository, relayDepositService,
                gofpRelayPointRepository, pushNotificationPort, deliveryNeedRepository, announcementRepository,
                otpService, deliveryOtpService, settlements, custodyTransferUseCase, missionUseCase,
                paymentUseCase, deliveryQueryUseCase, lifecycle, tenantContextHolder, new SimpleMeterRegistry());
        delivery = new Delivery();
        delivery.setId(DELIVERY);
        delivery.setDeliveryNeedId(NEED);
        delivery.setFreelancerId(PROFILE);
        delivery.setTarif(2500.0);
        DeliveryNeed need = new DeliveryNeed();
        need.setId(NEED);
        need.setUserId(CLIENT);
        when(deliveryRepository.findById(DELIVERY)).thenReturn(Mono.just(delivery));
        when(deliveryRepository.findByDeliveryNeedId(NEED)).thenReturn(Mono.just(delivery));
        when(deliveryNeedRepository.findById(NEED)).thenReturn(Mono.just(need));
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(TENANT));
        when(deliveryQueryUseCase.resolveActorIdForDeliveryPerson(TENANT, PROFILE)).thenReturn(Mono.just(COURIER));
        when(settlements.find(any(), eq(DeliveryPaymentApplicationService.REFERENCE_TYPE), eq(DELIVERY)))
                .thenReturn(Mono.empty());
    }

    private static TntSecurityContext user(UUID id, String... roles) {
        return TntSecurityContext.builder().userId(id).tenantId(TENANT).authenticated(true)
                .roles(Set.of(roles)).build();
    }

    @Test
    @DisplayName("the client of the delivery cannot change its price")
    void clientCannotChangePrice() {
        StepVerifier.create(rules.assertCallerCanUpdateDelivery(DELIVERY, true, user(CLIENT, "ROLE_CLIENT")))
                .expectError(AccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("the assigned delivery person cannot change its price either")
    void courierCannotChangePrice() {
        StepVerifier.create(rules.assertCallerCanUpdateDelivery(DELIVERY, true, user(COURIER, "ROLE_FREELANCER")))
                .expectError(AccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("staff can change the price before any payment step")
    void staffChangesPriceBeforePayment() {
        StepVerifier.create(rules.assertCallerCanUpdateDelivery(DELIVERY, true, user(STRANGER, "ROLE_SUPPORT_AGENT")))
                .verifyComplete();
        StepVerifier.create(rules.assertCallerCanUpdateDelivery(DELIVERY, true, user(STRANGER, "ROLE_TNT_ADMIN")))
                .verifyComplete();
    }

    @Test
    @DisplayName("once the delivery person generated a receive code, not even staff can change the price")
    void priceFrozenOnceReceiveCodeExists() {
        delivery.setReceiveCode("RC-1");
        StepVerifier.create(rules.assertCallerCanUpdateDelivery(DELIVERY, true, user(STRANGER, "ROLE_TNT_ADMIN")))
                .expectErrorMatches(e -> e instanceof ResponseStatusException r
                        && r.getStatusCode() == HttpStatus.CONFLICT)
                .verify();
    }

    @Test
    @DisplayName("once a settlement exists (paid or not), not even staff can change the price")
    void priceFrozenOnceSettled() {
        when(settlements.find(any(), eq(DeliveryPaymentApplicationService.REFERENCE_TYPE), eq(DELIVERY)))
                .thenReturn(Mono.just(WalletSettlement.builder().id(UUID.randomUUID()).build()));
        StepVerifier.create(rules.assertCallerCanUpdateDelivery(DELIVERY, true, user(STRANGER, "ROLE_TNT_ADMIN")))
                .expectErrorMatches(e -> e instanceof ResponseStatusException r
                        && r.getStatusCode() == HttpStatus.CONFLICT)
                .verify();
    }

    @Test
    @DisplayName("time windows stay editable by the parties, not by a stranger")
    void otherFieldsByParties() {
        StepVerifier.create(rules.assertCallerCanUpdateDelivery(DELIVERY, false, user(CLIENT))).verifyComplete();
        StepVerifier.create(rules.assertCallerCanUpdateDelivery(DELIVERY, false, user(STRANGER)))
                .expectError(AccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("the client, the assigned delivery person and staff read the delivery")
    void partiesRead() {
        StepVerifier.create(rules.assertCallerCanReadDelivery(DELIVERY, user(CLIENT))).verifyComplete();
        StepVerifier.create(rules.assertCallerCanReadDelivery(DELIVERY, user(COURIER))).verifyComplete();
        StepVerifier.create(rules.assertCallerCanReadDelivery(DELIVERY, user(STRANGER, "ROLE_SUPPORT_AGENT")))
                .verifyComplete();
        StepVerifier.create(rules.assertCallerCanReadDeliveryOfNeed(NEED, user(CLIENT))).verifyComplete();
    }

    @Test
    @DisplayName("anyone else is refused, by id or by delivery need; no identity is refused too")
    void strangerRefused() {
        StepVerifier.create(rules.assertCallerCanReadDelivery(DELIVERY, user(STRANGER, "ROLE_FREELANCER")))
                .expectError(AccessDeniedException.class).verify();
        StepVerifier.create(rules.assertCallerCanReadDeliveryOfNeed(NEED, user(STRANGER)))
                .expectError(AccessDeniedException.class).verify();
        StepVerifier.create(rules.assertCallerCanReadDelivery(DELIVERY, null))
                .expectError(AccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("an unknown delivery is a 404, not a 403")
    void unknownDelivery() {
        UUID other = UUID.randomUUID();
        when(deliveryRepository.findById(other)).thenReturn(Mono.empty());
        StepVerifier.create(rules.assertCallerCanReadDelivery(other, user(CLIENT)))
                .expectError(IllegalArgumentException.class).verify();
    }
}
