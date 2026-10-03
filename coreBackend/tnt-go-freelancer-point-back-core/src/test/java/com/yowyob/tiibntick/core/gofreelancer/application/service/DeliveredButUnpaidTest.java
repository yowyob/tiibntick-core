package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IIdempotencyStore;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IKernelPaymentGatewayPort;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IPaymentAnchorPort;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IPaymentIntentRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IWalletEventPublisher;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IWalletNotificationPort;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IWalletRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.service.WalletService;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletNotFoundException;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Wallet;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryStatusUpdateDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect;
import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
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
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot C-20.3 / C-20.4 — what happens when the payment of a direct delivery fails AFTER the
 * recipient's OTP was verified.
 *
 * <p>Observed and pinned here (not hypothesised):
 * <ul>
 *   <li>the delivery is saved {@code DELIVERED} first, exactly once, and stays so — nothing
 *       rolls it back or compensates;</li>
 *   <li>the error propagates to the caller (HTTP 5xx upstream);</li>
 *   <li>since C-20.3 the failure is also counted ({@code gofp.delivery.payment.failures})
 *       and logged {@code UNPAID_DELIVERED} — the only durable alert.</li>
 * </ul>
 *
 * <p>C-20.4: the payment path calls {@code getOrCreateWallet}, guarded by
 * {@code @RequirePermission(wallet:read)} on the CALLER (only the assigned delivery person can
 * reach this transition, see {@code GofpDeliveryController#updateStatus}). The real
 * {@link TntPermissionAspect} is wired around a real {@link WalletService}, and the caller is
 * shaped like a real Kernel token: no {@code roles}/{@code permissions} claims, only
 * {@code TENANT_<tid>}, so the decision falls to the local resolver — as in production.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DELIVERED then payment failure — delivered but unpaid")
class DeliveredButUnpaidTest {

    @Mock private DeliveryRepository gofpDeliveryRepository;
    @Mock private RelayDepositService relayDepositService;
    @Mock private GofpRelayPointRepository gofpRelayPointRepository;
    @Mock private PushNotificationPort pushNotificationPort;
    @Mock private IDeliveryNeedRepository deliveryNeedRepository;
    @Mock private IAnnouncementRepository announcementRepository;
    @Mock private OtpService otpService;
    @Mock private DeliveryOtpService deliveryOtpService;
    @Mock private RecordCustodyTransferUseCase custodyTransferUseCase;
    @Mock private RecordMissionUseCase missionUseCase;
    @Mock private RecordPaymentUseCase paymentUseCase;
    @Mock private DeliveryQueryUseCase deliveryQueryUseCase;
    @Mock private DeliveryLifecycleUseCase deliveryLifecycleUseCase;
    @Mock private TenantContextHolder tenantContextHolder;
    @Mock private IWalletRepository walletRepository;
    @Mock private ReactivePermissionResolver localResolver;

    private final UUID tenantId     = UUID.randomUUID();
    private final UUID deliveryId   = UUID.randomUUID();
    private final UUID profileId    = UUID.randomUUID();
    private final UUID courierActor = UUID.randomUUID();
    private final String otp = "123456";

    private SimpleMeterRegistry meters;
    private final java.util.List<DeliveryStatus> writes = new java.util.concurrent.CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();

        Delivery gofpDelivery = new Delivery();
        gofpDelivery.setId(deliveryId);
        gofpDelivery.setFreelancerId(profileId);
        gofpDelivery.setAnnouncementId(UUID.randomUUID()); // → ORANGE_MONEY split path
        gofpDelivery.setTarif(3000.0);
        gofpDelivery.setDeliveryOtpHash("hash");
        gofpDelivery.setStatus(DeliveryStatus.PICKED_UP);

        var coreDelivery = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery.builder()
                .id(deliveryId).tenantId(tenantId).deliveryPersonId(profileId)
                .status(com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus.PICKED_UP)
                .build();

        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(gofpDeliveryRepository.findById(deliveryId)).thenReturn(Mono.just(gofpDelivery));
        // The service assembles save() Monos it never subscribes (an eager `saveDelivery`);
        // only a subscription is a write, so record statuses at subscription time.
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

    // ── C-20.3 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("C-20.3 — payment fails after the OTP: DELIVERED stays saved, error propagates, failure counted")
    void paymentFailure_afterDeliveredSaved_statusStays_noCompensation_alertCounted() {
        IWalletUseCase wallet = mock(IWalletUseCase.class);
        when(wallet.getOrCreateWallet(courierActor, tenantId))
                .thenReturn(Mono.error(new WalletNotFoundException(courierActor)));

        StepVerifier.create(service(wallet).updateStatus(deliveryId, deliver()))
                .expectError(WalletNotFoundException.class)
                .verify();

        assertThat(writes)
                .as("exactly one write, DELIVERED, before the payment — nothing reverts it")
                .containsExactly(DeliveryStatus.DELIVERED);
        verify(wallet, never()).splitMissionRevenue(any());
        verify(pushNotificationPort, never()).sendPushNotification(any(), any(), any());
        assertThat(meters.get("gofp.delivery.payment.failures").counter().count()).isEqualTo(1.0);
    }

    // ── C-20.4 — real permission aspect around a real WalletService ──────

    @Test
    @DisplayName("C-20.4 — FREELANCER (local role, Kernel token without roles) passes the wallet:read guard")
    void freelancerCourier_passesWalletReadGuard() {
        when(localResolver.resolvePermissions(tenantId, courierActor))
                .thenReturn(Mono.just(permissionsOf(TntRole.FREELANCER)));
        when(walletRepository.findByUserId(courierActor, tenantId)).thenReturn(Mono.empty());
        when(walletRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(walletRepository.findByOwnerId(courierActor.toString(), tenantId))
                .thenReturn(Mono.just(Wallet.createNew(courierActor, tenantId, java.util.Currency.getInstance("XAF"))));
        when(walletRepository.saveTransaction(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service(guardedWalletService()).updateStatus(deliveryId, deliver())
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(kernelShapedCaller())))
                .assertNext(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.DELIVERED))
                .verifyComplete();
        assertThat(meters.get("gofp.delivery.payment.failures").counter().count()).isZero();
    }

    @Test
    @DisplayName("C-20.4 — PERMANENT_DELIVERER also holds wallet:read (the other delivery-person role)")
    void permanentDeliverer_holdsWalletRead() {
        assertThat(permissionsOf(TntRole.PERMANENT_DELIVERER)).contains("wallet:read");
        assertThat(permissionsOf(TntRole.FREELANCER)).contains("wallet:read");
    }

    @Test
    @DisplayName("C-20.4 — courier whose role was revoked after assignment: DELIVERED saved, payment refused, counted")
    void courierWithoutWalletRead_deliveredButUnpaid() {
        when(localResolver.resolvePermissions(tenantId, courierActor)).thenReturn(Mono.just(Set.of()));

        StepVerifier.create(service(guardedWalletService()).updateStatus(deliveryId, deliver())
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(kernelShapedCaller())))
                .expectError(TntRoleException.class)
                .verify();

        assertThat(writes).containsExactly(DeliveryStatus.DELIVERED);
        verify(walletRepository, never()).saveTransaction(any());
        assertThat(meters.get("gofp.delivery.payment.failures").counter().count()).isEqualTo(1.0);
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private DeliveryStatusApplicationService service(IWalletUseCase wallet) {
        DeliveryStatusApplicationService s = new DeliveryStatusApplicationService(
                gofpDeliveryRepository, relayDepositService, gofpRelayPointRepository,
                pushNotificationPort, deliveryNeedRepository, announcementRepository,
                otpService, deliveryOtpService, wallet,
                custodyTransferUseCase, missionUseCase, paymentUseCase,
                deliveryQueryUseCase, deliveryLifecycleUseCase, tenantContextHolder, meters);
        s.initMetrics();
        return s;
    }

    private IWalletUseCase guardedWalletService() {
        WalletService real = new WalletService(walletRepository,
                mock(IPaymentIntentRepository.class), mock(IIdempotencyStore.class),
                mock(IWalletEventPublisher.class, inv -> Mono.empty()),
                mock(IWalletNotificationPort.class, inv -> Mono.empty()),
                mock(IPaymentAnchorPort.class, inv -> Mono.empty()),
                mock(IKernelPaymentGatewayPort.class));
        TntPermissionAspect aspect = new TntPermissionAspect(
                new TntPermissionEvaluator(localResolver, new TntRoleDefinitionRegistry()));
        AspectJProxyFactory factory = new AspectJProxyFactory(real);
        // Class-based (CGLIB) proxy, as Spring Boot creates by default. A JDK interface proxy
        // would hand the aspect the IWalletUseCase method, which carries no @RequirePermission:
        // TntPermissionAspect then proceeds unguarded (fail-open) — see the C-20 report.
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    /** Same shape as a real Kernel access token after TntSecurityConfig's converter. */
    private TestingAuthenticationToken kernelShapedCaller() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(courierActor.toString(), "n/a",
                List.of(new SimpleGrantedAuthority("TENANT_" + tenantId)));
        auth.setAuthenticated(true);
        return auth;
    }

    private static Set<String> permissionsOf(TntRole role) {
        return new TntRoleDefinitionRegistry().getByCode(role.name()).defaultPermissions().stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    private DeliveryStatusUpdateDTO deliver() {
        DeliveryStatusUpdateDTO dto = new DeliveryStatusUpdateDTO();
        dto.setStatus(DeliveryStatus.DELIVERED);
        dto.setConfirmationCode(otp);
        return dto;
    }
}
