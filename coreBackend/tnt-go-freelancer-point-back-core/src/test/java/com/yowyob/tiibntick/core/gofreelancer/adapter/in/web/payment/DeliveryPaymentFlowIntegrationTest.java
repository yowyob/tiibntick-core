package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.payment;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.incident.IncidentPaymentFreezeAdapter;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.KernelSettlementAdapter;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IPaymentMirrorRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.ISettlementRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.service.CashCollectionService;
import com.yowyob.tiibntick.core.billing.wallet.application.service.EscrowService;
import com.yowyob.tiibntick.core.billing.wallet.application.service.PaymentLifecycleService;
import com.yowyob.tiibntick.core.billing.wallet.application.service.SettlementService;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.CommissionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementMethod;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.CashCollectionMirror;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowMirror;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntent;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletSettlement;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.SystemInvoicingUseCase;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.GenerateInvoiceCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryPaymentApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryStatusApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntRoleExceptionHandler;
import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration test of the payment of an elected delivery into a Kernel escrow (lot L5) —
 * {@code DeliveryPaymentController -> DeliveryPaymentApplicationService -> EscrowService ->
 * KernelSettlementAdapter -> HTTP} — against a {@link MockWebServer} playing the Kernel. The Kernel tells the
 * client's calls (her bearer, forwarded) from the platform's (no bearer: service account).
 *
 * <p>Real: the controller (behind the real {@code @RequirePermission} aspect), the payment, escrow, settlement and
 * lifecycle services, the incident freeze adapter, the Kernel HTTP adapter with the production Jackson 2 codecs.
 * Faked: persistence (in-memory) and the delivery/announcement repositories.
 */
@DisplayName("Delivery payment into a Kernel escrow — end to end against a fake Kernel")
class DeliveryPaymentFlowIntegrationTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID DRIVER = UUID.randomUUID();
    private static final UUID DRIVER_PROFILE = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final UUID DELIVERY_ID = UUID.randomUUID();
    private static final UUID ANNOUNCEMENT_ID = UUID.randomUUID();
    private static final UUID WALLET_C = UUID.randomUUID();
    private static final UUID WALLET_D = UUID.randomUUID();
    private static final UUID WALLET_P = UUID.randomUUID();
    private static final UUID ESCROW_ID = UUID.randomUUID();
    private static final String BASE = "/api/v1/deliveries/" + DELIVERY_ID + "/payment";

    private MockWebServer kernel;
    private final List<RecordedRequest> seen = new CopyOnWriteArrayList<>();
    /** Bearer "forwarded" by the Core on the user client: the user making the HTTP call. */
    private final AtomicReference<UUID> bearer = new AtomicReference<>();
    /** Kernel escrow state ("" = none). */
    private final AtomicReference<String> escrowStatus = new AtomicReference<>("");

    private final Map<UUID, WalletSettlement> settlementStore = new ConcurrentHashMap<>();
    private final InMemoryMirrors mirrors = new InMemoryMirrors();
    private Delivery delivery;
    private DeliveryNeed need;
    private WebTestClient web;
    private EscrowService escrows;
    private PaymentLifecycleService lifecycle;
    /** Stands for the invoice module: captures what the payment flow asks it to issue. */
    private final SystemInvoicingUseCase invoicing = mock(SystemInvoicingUseCase.class);
    private final List<GenerateInvoiceCommand> invoiceCommands = new CopyOnWriteArrayList<>();
    private final List<String> invoicePaymentRefs = new CopyOnWriteArrayList<>();
    private boolean invoicingFails;
    /** H5 extension point plugged into the service (null: the service's fixed 5 %). */
    private com.yowyob.tiibntick.core.gofreelancer.application.port.out.CommissionRatePort commissionRates;

    @BeforeEach
    void setUp() throws IOException {
        kernel = new MockWebServer();
        kernel.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                seen.add(request);
                String path = request.getPath();
                String auth = request.getHeader("Authorization");
                String body = request.getBody().clone().readUtf8();
                boolean asClient = ("Bearer " + CLIENT).equals(auth);
                boolean asSystem = auth == null;
                if (path.equals("/api/payments/wallets/owner/" + CLIENT)) {
                    return json(200, wallet(WALLET_C, CLIENT, 50000));
                }
                if (path.equals("/api/payments/wallets/owner/" + DRIVER)) {
                    return json(200, wallet(WALLET_D, DRIVER, 0));
                }
                if (path.equals("/api/payments/escrows/challenge") && asClient) {
                    return json(200, "{\"success\":true,\"data\":{\"challengeToken\":\"chal-1\","
                            + "\"expiresInSeconds\":300,\"codePreview\":\"***123\"}}");
                }
                if (path.startsWith("/api/payments/escrows?") && asSystem) {
                    return escrowStatus.get().isEmpty()
                            ? json(404, "{\"success\":false,\"message\":\"no escrow\"}") : json(200, escrow());
                }
                if (path.equals("/api/payments/escrows") && asClient) {
                    if (body.contains("\"code\":\"000000\"")) {
                        return json(400, "{\"success\":false,\"message\":\"Invalid confirmation code\"}");
                    }
                    escrowStatus.set("HELD");
                    return json(200, escrow());
                }
                if (path.equals("/api/payments/escrows/" + ESCROW_ID + "/release") && asSystem) {
                    escrowStatus.set("RELEASED");
                    return json(200, escrow());
                }
                if (path.equals("/api/payments/escrows/" + ESCROW_ID + "/freeze") && asSystem) {
                    escrowStatus.set("FROZEN");
                    return json(200, escrow());
                }
                if (path.equals("/api/payments/escrows/" + ESCROW_ID + "/unfreeze") && asSystem) {
                    escrowStatus.set("HELD");
                    return json(200, escrow());
                }
                return json(404, "{\"success\":false,\"message\":\"unexpected " + path + " as " + auth + "\"}");
            }
        });
        kernel.start();

        delivery = new Delivery();
        delivery.setId(DELIVERY_ID);
        delivery.setAnnouncementId(ANNOUNCEMENT_ID);
        com.yowyob.tiibntick.core.gofreelancer.TestDeliveries.driver(delivery, DRIVER_PROFILE);
        com.yowyob.tiibntick.core.gofreelancer.TestDeliveries.tarif(delivery, 3000.0);
        com.yowyob.tiibntick.core.gofreelancer.TestDeliveries.status(delivery, DeliveryStatus.PICKED_UP);
        need = null;
        web = buildClient();
    }

    @AfterEach
    void tearDown() throws IOException {
        kernel.shutdown();
    }

    // ── happy path ───────────────────────────────────────────────────────

    @Test
    @DisplayName("H5: the default commission adapter keeps 5 %; a rate from the extension point is the PLATFORM share; a failing one falls back to 5 %")
    void commissionRateExtensionPoint() {
        reactor.test.StepVerifier.create(new com.yowyob.tiibntick.core.gofreelancer.adapter.out.pricing.FixedCommissionRateAdapter()
                        .deliveryCommissionRate(TENANT, DELIVERY_ID))
                .expectNextMatches(r -> r.compareTo(new java.math.BigDecimal("0.05")) == 0).verifyComplete();

        commissionRates = (t, d) -> Mono.just(new java.math.BigDecimal("0.10"));
        web = buildClient();
        post(CLIENT, BASE + "/challenge", null).expectStatus().isOk();
        assertThat(lastTo("/api/payments/escrows/challenge").getBody().readUtf8())
                .contains("\"role\":\"PLATFORM\",\"walletId\":\"" + WALLET_P + "\",\"amount\":300",
                        "\"role\":\"COURIER\",\"walletId\":\"" + WALLET_D + "\",\"amount\":2700");

        commissionRates = (t, d) -> Mono.error(new IllegalStateException("billing down"));
        web = buildClient();
        post(CLIENT, BASE + "/challenge", null).expectStatus().isOk();
        assertThat(lastTo("/api/payments/escrows/challenge").getBody().readUtf8())
                .contains("\"amount\":150", "\"amount\":2850");
    }

    @Test
    @DisplayName("client challenge then confirm: escrow HELD with her code, invoice issued; delivered → released, PAID by ESCROW, commission SETTLED")
    void fullEscrowFlow() {
        post(CLIENT, BASE + "/challenge", null).expectStatus().isOk()
                .expectBody()
                .jsonPath("$.challengeToken").isEqualTo("chal-1")
                .jsonPath("$.amount").isEqualTo(3000)
                .jsonPath("$.currency").isEqualTo("XAF");
        RecordedRequest challenge = lastTo("/api/payments/escrows/challenge");
        assertThat(challenge.getHeader("Authorization")).isEqualTo("Bearer " + CLIENT);
        assertThat(challenge.getBody().readUtf8()).contains("\"subjectType\":\"DELIVERY\"",
                "\"subjectId\":\"" + DELIVERY_ID + "\"", "\"payerWalletId\":\"" + WALLET_C + "\"")
                .doesNotContain("\"code\"");

        post(CLIENT, BASE, "{\"challengeToken\":\"chal-1\",\"code\":\"123456\"}")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("HELD")
                .jsonPath("$.escrowStatus").isEqualTo("HELD")
                .jsonPath("$.method").isEqualTo("ESCROW");
        String hold = lastTo("/api/payments/escrows").getBody().readUtf8();
        assertThat(hold).contains("\"challengeToken\":\"chal-1\"", "\"code\":\"123456\"");
        assertThat(mirrors.escrows.get(DELIVERY_ID).status()).isEqualTo(EscrowMirror.Status.HELD);
        assertThat(settlementStore).as("settled at the release, not at the hold").isEmpty();

        // the paid invoice: client = payer, issuer = the courier's account, escrow id as payment reference
        assertThat(invoiceCommands).singleElement().satisfies(c -> {
            assertThat(c.tenantId()).isEqualTo(TENANT);
            assertThat(c.deliveryId()).isEqualTo(DELIVERY_ID.toString());
            assertThat(c.clientId()).isEqualTo(CLIENT.toString());
            assertThat(c.issuerOrgId()).isEqualTo(DRIVER.toString());
            assertThat(c.lines()).singleElement().satisfies(l ->
                    assertThat(l.lineTotal().amount().doubleValue()).isEqualTo(3000.0));
        });
        assertThat(invoicePaymentRefs).containsExactly(ESCROW_ID.toString());

        // both parties can read it; a stranger cannot
        get(DRIVER).expectStatus().isOk().expectBody().jsonPath("$.escrowStatus").isEqualTo("HELD");
        get(STRANGER).expectStatus().isForbidden();

        // paying twice is refused and does not hit the Kernel again
        int holdsBefore = countTo("/api/payments/escrows/challenge");
        post(CLIENT, BASE + "/challenge", null).expectStatus().isEqualTo(409);
        assertThat(countTo("/api/payments/escrows/challenge")).isEqualTo(holdsBefore);

        // DeliveryDelivered → released by the platform (no bearer), settlement PAID by ESCROW
        lifecycle.delivered(DELIVERY_ID, new java.math.BigDecimal("3000")).block();
        assertThat(lastTo("/api/payments/escrows/" + ESCROW_ID + "/release").getHeader("Authorization")).isNull();
        WalletSettlement s = settlementStore.values().iterator().next();
        assertThat(s.getMethod()).isEqualTo(SettlementMethod.ESCROW);
        assertThat(s.getPayerUserId()).isEqualTo(CLIENT);
        assertThat(s.getPayeeUserId()).isEqualTo(DRIVER);
        assertThat(s.getKernelReference()).isEqualTo(ESCROW_ID.toString());
        assertThat(s.getCommissionStatus()).isEqualTo(CommissionStatus.SETTLED);
        assertThat(s.getCommissionAmount()).isEqualByComparingTo("150.00");
        get(CLIENT).expectStatus().isOk().expectBody()
                .jsonPath("$.status").isEqualTo("PAID")
                .jsonPath("$.escrowStatus").isEqualTo("RELEASED");

        // replayed event: nothing more
        int releases = countTo("/release");
        lifecycle.delivered(DELIVERY_ID, new java.math.BigDecimal("3000")).block();
        assertThat(countTo("/release")).isEqualTo(releases);
    }

    @Test
    @DisplayName("paid after the hand-over: the escrow is released at once")
    void paidAfterDeliveryIsReleasedAtOnce() {
        com.yowyob.tiibntick.core.gofreelancer.TestDeliveries.status(delivery, DeliveryStatus.DELIVERED);
        post(CLIENT, BASE + "/challenge", null).expectStatus().isOk();
        post(CLIENT, BASE, "{\"challengeToken\":\"chal-1\",\"code\":\"123456\"}")
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("PAID")
                .jsonPath("$.escrowStatus").isEqualTo("RELEASED");
        assertThat(countTo("/release")).isEqualTo(1);
    }

    @Test
    @DisplayName("incident freeze and unfreeze go to the escrow; a dispute's freeze is not lifted by the incident")
    void incidentFreezeOnEscrow() {
        post(CLIENT, BASE + "/challenge", null).expectStatus().isOk();
        post(CLIENT, BASE, "{\"challengeToken\":\"chal-1\",\"code\":\"123456\"}").expectStatus().isOk();
        IncidentPaymentFreezeAdapter freeze = new IncidentPaymentFreezeAdapter(escrows);

        freeze.freezePayment(DELIVERY_ID, "escalated").block();
        assertThat(mirrors.escrows.get(DELIVERY_ID).status()).isEqualTo(EscrowMirror.Status.FROZEN);
        freeze.unfreezePayment(DELIVERY_ID, "resolved").block();
        assertThat(mirrors.escrows.get(DELIVERY_ID).status()).isEqualTo(EscrowMirror.Status.HELD);

        freeze.freezePayment(DELIVERY_ID, "escalated").block();
        UUID dispute = UUID.randomUUID();
        lifecycle.disputeOpened(DELIVERY_ID, dispute).block();
        assertThat(mirrors.escrows.get(DELIVERY_ID).reason()).isEqualTo("DISPUTE:" + dispute);
        int unfreezes = countTo("/unfreeze");
        freeze.unfreezePayment(DELIVERY_ID, "incident closed").block();
        assertThat(countTo("/unfreeze")).isEqualTo(unfreezes);
        assertThat(mirrors.escrows.get(DELIVERY_ID).status()).isEqualTo(EscrowMirror.Status.FROZEN);

        lifecycle.disputeClosed(DELIVERY_ID, dispute, null, null).block();
        assertThat(mirrors.escrows.get(DELIVERY_ID).status()).isEqualTo(EscrowMirror.Status.HELD);
    }

    // ── refusals ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("confirming without a started payment: 409, Kernel untouched")
    void confirmWithoutChallenge_409() {
        post(CLIENT, BASE, "{\"challengeToken\":\"chal-1\",\"code\":\"123456\"}").expectStatus().isEqualTo(409);
        assertThat(countTo("/api/payments/escrows")).isZero();
    }

    @Test
    @DisplayName("only the client pays: the driver and strangers get 403")
    void roleChecks() {
        post(DRIVER, BASE + "/challenge", null).expectStatus().isForbidden();
        post(STRANGER, BASE + "/challenge", null).expectStatus().isForbidden();
        post(STRANGER, BASE, "{\"challengeToken\":\"chal-1\",\"code\":\"123456\"}").expectStatus().isForbidden();
        assertThat(countTo("/api/payments/escrows")).isZero();
    }

    @Test
    @DisplayName("wrong confirmation code: the Kernel's refusal is a 409 KERNEL_PAYMENT_REFUSED, nothing recorded")
    void wrongConfirmationCode_nothingRecorded() {
        post(CLIENT, BASE + "/challenge", null).expectStatus().isOk();
        post(CLIENT, BASE, "{\"challengeToken\":\"chal-1\",\"code\":\"000000\"}")
                .expectStatus().isEqualTo(409)
                .expectBody(String.class).value(b -> assertThat(b).contains("KERNEL_PAYMENT_REFUSED",
                        "Invalid confirmation code"));
        assertThat(mirrors.escrows).isEmpty();
        assertThat(settlementStore).isEmpty();
        get(CLIENT).expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("NOT_SETTLED");

        // a new challenge replaces the unused intent; the right code then holds
        post(CLIENT, BASE + "/challenge", null).expectStatus().isOk();
        assertThat(mirrors.intents).hasSize(1);
        post(CLIENT, BASE, "{\"challengeToken\":\"chal-1\",\"code\":\"123456\"}").expectStatus().isOk();
    }

    @Test
    @DisplayName("an invoicing failure never turns a held payment into an error")
    void invoiceFailureDoesNotBreakPayment() {
        invoicingFails = true;
        post(CLIENT, BASE + "/challenge", null).expectStatus().isOk();
        post(CLIENT, BASE, "{\"challengeToken\":\"chal-1\",\"code\":\"123456\"}")
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("HELD");
        assertThat(invoiceCommands).isEmpty();
    }

    @Test
    @DisplayName("a CASH delivery cannot be paid into an escrow (409)")
    void cashDeliveryRefused() {
        delivery.setAnnouncementId(null);
        delivery.setDeliveryNeedId(UUID.randomUUID());
        need = DeliveryNeed.builder().id(delivery.getDeliveryNeedId()).userId(CLIENT).paymentMethod("CASH").build();
        post(CLIENT, BASE + "/challenge", null).expectStatus().isEqualTo(409);
        assertThat(countTo("/api/payments/escrows")).isZero();
    }

    @Test
    @DisplayName("the payment endpoints are permission-guarded: no payment:process → 403, nothing reaches the Kernel")
    void permissionGuard() {
        bearer.set(CLIENT);
        WebTestClient noPerms = buildClientWithAuthorities();
        noPerms.post().uri(BASE + "/challenge").header("X-Test-User", CLIENT.toString())
                .exchange().expectStatus().isForbidden();
        assertThat(seen).isEmpty();
    }

    // ── harness ──────────────────────────────────────────────────────────

    private WebTestClient.ResponseSpec post(UUID user, String uri, String body) {
        bearer.set(user);
        var spec = web.post().uri(uri).header("X-Test-User", user.toString());
        if (body != null) {
            return spec.contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
        }
        return spec.exchange();
    }

    private WebTestClient.ResponseSpec get(UUID user) {
        bearer.set(user);
        return web.get().uri(BASE).header("X-Test-User", user.toString()).exchange();
    }

    private WebTestClient buildClient() {
        return buildClientWithAuthorities("payment:process", "wallet:read");
    }

    private WebTestClient buildClientWithAuthorities(String... authorities) {
        JsonMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .visibility(PropertyAccessor.FIELD, Visibility.ANY)
                .visibility(PropertyAccessor.GETTER, Visibility.NONE)
                .visibility(PropertyAccessor.IS_GETTER, Visibility.NONE)
                .build();
        WebClient kernelClient = WebClient.builder()
                .exchangeStrategies(ExchangeStrategies.builder().codecs(c -> {
                    c.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(mapper));
                    c.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper));
                }).build())
                .baseUrl(kernel.url("/").toString())
                // stands for KernelBridgeConfig#propagateBearerToken: the caller's own token goes to the Kernel
                .filter((req, next) -> next.exchange(org.springframework.web.reactive.function.client.ClientRequest
                        .from(req).header("Authorization", "Bearer " + bearer.get()).build()))
                .build();

        WebClient systemClient = kernelClient.mutate().filters(f -> f.clear()).build();
        KernelSettlementAdapter settlementAdapter = new KernelSettlementAdapter(kernelClient, systemClient);
        SettlementService settlements = new SettlementService(settlementRepo());
        escrows = new EscrowService(settlementAdapter, mirrors, settlements, WALLET_P, java.time.Clock.systemUTC());
        lifecycle = new PaymentLifecycleService(escrows,
                new CashCollectionService(settlementAdapter, mirrors, settlements));
        DeliveryQueryUseCase deliveryQuery = mock(DeliveryQueryUseCase.class);
        when(deliveryQuery.resolveActorIdForDeliveryPerson(any(), eq(DRIVER_PROFILE))).thenReturn(Mono.just(DRIVER));

        DeliveryRepository deliveries = mock(DeliveryRepository.class);
        when(deliveries.findById(DELIVERY_ID)).thenAnswer(inv -> Mono.just(delivery));
        when(deliveries.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        IAnnouncementRepository announcements = mock(IAnnouncementRepository.class);
        Announcement announcement = new Announcement();
        announcement.setId(ANNOUNCEMENT_ID);
        announcement.setClientId(CLIENT);
        announcement.setPaymentMethod("ORANGE_MONEY");
        when(announcements.findById(ANNOUNCEMENT_ID)).thenReturn(Mono.just(announcement));
        IDeliveryNeedRepository needs = mock(IDeliveryNeedRepository.class);
        when(needs.findById(any())).thenAnswer(inv -> Mono.justOrEmpty(need));
        DeliveryStatusApplicationService status = mock(DeliveryStatusApplicationService.class);
        when(status.assertCallerIsAssignedDeliveryPerson(eq(DELIVERY_ID), any())).thenAnswer(inv ->
                DRIVER.equals(inv.getArgument(1)) ? Mono.<Void>empty()
                        : Mono.<Void>error(new AccessDeniedException("not the assigned delivery person")));
        TenantContextHolder tenantHolder = mock(TenantContextHolder.class);
        when(tenantHolder.currentTenantId()).thenReturn(Mono.just(TENANT));

        when(invoicing.issuePaidInvoiceAsSystem(any(), any())).thenAnswer(inv -> {
            if (invoicingFails) {
                return Mono.error(new IllegalStateException("invoice sequence unavailable"));
            }
            invoiceCommands.add(inv.getArgument(0));
            invoicePaymentRefs.add(inv.getArgument(1));
            return Mono.just(mock(com.yowyob.tiibntick.core.billing.invoice.domain.model.Invoice.class));
        });
        com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository users =
                mock(com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository.class);
        when(users.findByCoreUserId(any())).thenReturn(Mono.empty());

        DeliveryPaymentApplicationService service = new DeliveryPaymentApplicationService(
                deliveries, announcements, needs, status, escrows, settlements, deliveryQuery, tenantHolder,
                invoicing, users);
        if (commissionRates != null) service.setCommissionRates(commissionRates);

        var auth = new TestingAuthenticationToken("user", "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        auth.setAuthenticated(true);
        TntPermissionEvaluator evaluator = new TntPermissionEvaluator(
                mock(ReactivePermissionResolver.class), new TntRoleDefinitionRegistry());
        AspectJProxyFactory factory = new AspectJProxyFactory(new DeliveryPaymentController(service));
        factory.setProxyTargetClass(true);
        factory.addAspect(new TntPermissionAspect(evaluator));
        DeliveryPaymentController controller = factory.getProxy();

        return WebTestClient.bindToController(controller)
                .httpMessageCodecs(c -> {
                    c.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(mapper));
                    c.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper));
                })
                .controllerAdvice(new TntRoleExceptionHandler())
                .argumentResolvers(cfg -> cfg.addCustomResolver(new HeaderUserResolver()))
                .webFilter((exchange, chain) -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .build();
    }

    /** Builds the {@code @CurrentUser} context from the {@code X-Test-User} header. */
    static class HeaderUserResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter p) {
            return p.hasParameterAnnotation(CurrentUser.class)
                    && TntSecurityContext.class.isAssignableFrom(p.getParameterType());
        }

        @Override
        public Mono<Object> resolveArgument(MethodParameter p, BindingContext ctx, ServerWebExchange exchange) {
            String user = exchange.getRequest().getHeaders().getFirst("X-Test-User");
            return Mono.justOrEmpty(user == null ? null
                    : TntSecurityContext.builder().userId(UUID.fromString(user)).tenantId(TENANT).build());
        }
    }

    private ISettlementRepository settlementRepo() {
        return new ISettlementRepository() {
            @Override
            public Mono<WalletSettlement> save(WalletSettlement s) {
                settlementStore.put(s.getId(), s.toBuilder().persisted(true).build());
                return Mono.just(settlementStore.get(s.getId()));
            }

            @Override
            public Mono<WalletSettlement> findById(UUID id) {
                return Mono.justOrEmpty(settlementStore.get(id));
            }

            @Override
            public Mono<WalletSettlement> findByReference(UUID tenantId, String type, UUID refId) {
                return Flux.fromIterable(settlementStore.values())
                        .filter(s -> s.getTenantId().equals(tenantId) && s.getReferenceType().equals(type)
                                && s.getReferenceId().equals(refId)).next();
            }

            @Override
            public Mono<WalletSettlement> findByReference(String type, UUID refId) {
                return Flux.fromIterable(settlementStore.values())
                        .filter(s -> s.getReferenceType().equals(type) && s.getReferenceId().equals(refId)).next();
            }

            @Override
            public Flux<WalletSettlement> findByTenantAndCommissionStatus(UUID t, CommissionStatus st) {
                return Flux.fromIterable(settlementStore.values())
                        .filter(s -> s.getTenantId().equals(t) && s.getCommissionStatus() == st);
            }
        };
    }

    /** In-memory {@link IPaymentMirrorRepository}. */
    static final class InMemoryMirrors implements IPaymentMirrorRepository {
        final Map<UUID, PaymentIntent> intents = new ConcurrentHashMap<>();
        final Map<UUID, EscrowMirror> escrows = new ConcurrentHashMap<>();
        final Map<UUID, CashCollectionMirror> cash = new ConcurrentHashMap<>();

        @Override
        public Mono<PaymentIntent> findIntent(UUID intentId) {
            return Mono.justOrEmpty(intents.get(intentId));
        }

        @Override
        public Mono<PaymentIntent> findIntentByDelivery(UUID deliveryId) {
            return Flux.fromIterable(intents.values()).filter(i -> i.deliveryId().equals(deliveryId)).next();
        }

        @Override
        public Mono<PaymentIntent> saveIntent(PaymentIntent intent, boolean isNew) {
            if (isNew && intents.values().stream().anyMatch(i -> i.deliveryId().equals(intent.deliveryId()))) {
                return Mono.error(new IllegalStateException("duplicate delivery_id"));
            }
            intents.put(intent.id(), intent);
            return Mono.just(intent);
        }

        @Override
        public Mono<EscrowMirror> findEscrow(UUID deliveryId, String purpose) {
            return Mono.justOrEmpty(escrows.get(deliveryId)).filter(e -> e.purpose().equals(purpose));
        }

        @Override
        public Mono<EscrowMirror> saveEscrow(EscrowMirror escrow, boolean isNew) {
            escrows.put(escrow.deliveryId(), escrow);
            return Mono.just(escrow);
        }

        @Override
        public Mono<CashCollectionMirror> findCashCollection(UUID deliveryId) {
            return Mono.justOrEmpty(cash.get(deliveryId));
        }

        @Override
        public Mono<CashCollectionMirror> saveCashCollection(CashCollectionMirror c, boolean isNew) {
            cash.put(c.deliveryId(), c);
            return Mono.just(c);
        }

        @Override
        public Flux<CashCollectionMirror> outstandingCashCollections(UUID courierActorId) {
            return Flux.fromIterable(cash.values()).filter(c -> c.courierActorId().equals(courierActorId));
        }
    }

    private String escrow() {
        return "{\"success\":true,\"data\":{\"id\":\"" + ESCROW_ID + "\",\"subjectType\":\"DELIVERY\","
                + "\"subjectId\":\"" + DELIVERY_ID + "\",\"amount\":3000,\"currency\":\"XAF\",\"status\":\""
                + escrowStatus.get() + "\"}}";
    }

    private static String wallet(UUID id, UUID owner, int balance) {
        return "{\"success\":true,\"data\":{\"id\":\"" + id + "\",\"ownerId\":\"" + owner
                + "\",\"ownerType\":\"USER\",\"ownerName\":\"T\",\"balance\":" + balance + "}}";
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body);
    }

    private RecordedRequest lastTo(String path) {
        return seen.stream().filter(r -> r.getPath().equals(path)).reduce((a, b) -> b).orElseThrow();
    }

    private int countTo(String pathFragment) {
        return (int) seen.stream().filter(r -> r.getPath().contains(pathFragment)).count();
    }
}
