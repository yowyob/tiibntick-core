package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.payment;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.KernelPaymentGatewayAdapter;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.ISettlementRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IWalletHoldRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.service.KernelWalletService;
import com.yowyob.tiibntick.core.billing.wallet.application.service.SettlementService;
import com.yowyob.tiibntick.core.billing.wallet.application.service.WalletHoldService;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.CommissionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.HoldStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletHold;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletSettlement;
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
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
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
 * Integration test of the whole delivery payment chain —
 * {@code DeliveryPaymentController -> DeliveryPaymentApplicationService -> KernelWalletService ->
 * KernelPaymentGatewayAdapter -> HTTP} — against a {@link MockWebServer} playing the Kernel, which
 * tells the driver's wallet from the client's by the bearer token forwarded by the Core.
 *
 * <p>Real: the controller (behind the real {@code @RequirePermission} aspect), the payment,
 * wallet, settlement and hold services, the Kernel HTTP adapter with the production Jackson 2
 * codecs. Faked: persistence (in-memory) and the delivery/announcement repositories.
 *
 * @author MANFOUO Braun
 */
@DisplayName("Delivery payment by Kernel wallet transfer — end to end against a fake Kernel")
class DeliveryPaymentFlowIntegrationTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID DRIVER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final UUID DELIVERY_ID = UUID.randomUUID();
    private static final UUID ANNOUNCEMENT_ID = UUID.randomUUID();
    private static final UUID WALLET_C = UUID.randomUUID();
    private static final UUID WALLET_D = UUID.randomUUID();
    private static final String BASE = "/api/v1/deliveries/" + DELIVERY_ID + "/payment";

    private MockWebServer kernel;
    private final List<RecordedRequest> seen = new CopyOnWriteArrayList<>();
    /** Bearer "forwarded" by the Core: the test sets it to the user making the HTTP call. */
    private final AtomicReference<UUID> bearer = new AtomicReference<>();

    private final Map<UUID, WalletSettlement> settlementStore = new ConcurrentHashMap<>();
    private final Map<UUID, WalletHold> holdStore = new ConcurrentHashMap<>();
    private Delivery delivery;
    private DeliveryNeed need;
    private WebTestClient web;
    private WalletHoldService holdService;
    /** Stands for the invoice module: captures what the payment flow asks it to issue. */
    private final SystemInvoicingUseCase invoicing = mock(SystemInvoicingUseCase.class);
    private final List<GenerateInvoiceCommand> invoiceCommands = new CopyOnWriteArrayList<>();
    private final List<String> invoicePaymentRefs = new CopyOnWriteArrayList<>();
    private boolean invoicingFails;

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
                boolean asDriver = ("Bearer " + DRIVER).equals(auth);
                boolean asClient = ("Bearer " + CLIENT).equals(auth);
                if (path.equals("/api/payments/wallets/mine")) {
                    if (asDriver) {
                        return json(200, wallet(WALLET_D, DRIVER, 0));
                    }
                    if (asClient) {
                        return json(200, wallet(WALLET_C, CLIENT, 50000));
                    }
                    return json(404, "{\"success\":false,\"message\":\"no wallet\"}");
                }
                if (path.equals("/api/payments/wallets/" + WALLET_D + "/receive-codes")) {
                    return json(200, "{\"success\":true,\"data\":{\"code\":\"RC-777\",\"expiresAt\":\""
                            + Instant.now().plusSeconds(600) + "\"}}");
                }
                if (path.equals("/api/payments/wallets/" + WALLET_C + "/transfers/challenge")) {
                    return json(200, "{\"success\":true,\"data\":{\"challengeToken\":\"chal-1\","
                            + "\"expiresInSeconds\":300,\"codePreview\":\"***123\"}}");
                }
                if (path.equals("/api/payments/wallets/" + WALLET_C + "/transfers")) {
                    if (body.contains("\"code\":\"000000\"")) {
                        return json(400, "{\"success\":false,\"message\":\"Invalid confirmation code\"}");
                    }
                    return json(200, "{\"success\":true,\"data\":{\"transactionId\":\"tx-42\","
                            + "\"status\":\"COMPLETED\"}}");
                }
                return json(404, "{\"success\":false,\"message\":\"unexpected " + path + "\"}");
            }
        });
        kernel.start();

        delivery = new Delivery();
        delivery.setId(DELIVERY_ID);
        delivery.setAnnouncementId(ANNOUNCEMENT_ID);
        delivery.setFreelancerId(UUID.randomUUID());
        delivery.setTarif(3000.0);
        delivery.setStatus(DeliveryStatus.PICKED_UP);
        need = null;
        web = buildClient();
    }

    @AfterEach
    void tearDown() throws IOException {
        kernel.shutdown();
    }

    // ── happy path ───────────────────────────────────────────────────────

    @Test
    @DisplayName("driver stores a receive code, client gets a challenge and confirms: PAID, commission 5% DUE, Kernel got the right calls")
    void fullPaymentFlow() {
        bearer.set(DRIVER);
        post(DRIVER, BASE + "/receive-code", null).expectStatus().isOk()
                .expectBody().jsonPath("$.code").isEqualTo("RC-777");
        assertThat(delivery.getReceiveCode()).isEqualTo("RC-777");
        assertThat(delivery.getDriverUserId()).isEqualTo(DRIVER);

        bearer.set(CLIENT);
        post(CLIENT, BASE + "/challenge", null).expectStatus().isOk()
                .expectBody()
                .jsonPath("$.challengeToken").isEqualTo("chal-1")
                .jsonPath("$.amount").isEqualTo(3000.00)
                .jsonPath("$.currency").isEqualTo("XAF");
        RecordedRequest challenge = lastTo("/api/payments/wallets/" + WALLET_C + "/transfers/challenge");
        String challengeBody = challenge.getBody().readUtf8();
        assertThat(challengeBody).contains("\"recipientCode\":\"RC-777\"", "\"amount\":3000.00",
                "\"reference\":\"DELIVERY:" + DELIVERY_ID + "\"");
        assertThat(challenge.getHeader("Authorization")).isEqualTo("Bearer " + CLIENT);

        post(CLIENT, BASE, "{\"challengeToken\":\"chal-1\",\"code\":\"123456\"}")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PAID")
                .jsonPath("$.method").isEqualTo("WALLET_TRANSFER")
                .jsonPath("$.commissionStatus").isEqualTo("DUE")
                .jsonPath("$.commissionAmount").isEqualTo(150.00);

        String transfer = lastTo("/api/payments/wallets/" + WALLET_C + "/transfers").getBody().readUtf8();
        assertThat(transfer).contains("\"recipientCode\":\"RC-777\"", "\"challengeToken\":\"chal-1\"",
                "\"code\":\"123456\"");

        WalletSettlement s = settlementStore.values().iterator().next();
        assertThat(s.getPayerUserId()).isEqualTo(CLIENT);
        assertThat(s.getPayeeUserId()).isEqualTo(DRIVER);
        assertThat(s.getKernelReference()).isEqualTo("tx-42");
        assertThat(s.getCommissionStatus()).isEqualTo(CommissionStatus.DUE);

        // the paid invoice is issued once: client = payer, issuer = the delivery person, Kernel tx as payment ref
        assertThat(invoiceCommands).singleElement().satisfies(c -> {
            assertThat(c.tenantId()).isEqualTo(TENANT);
            assertThat(c.missionId()).isEqualTo(DELIVERY_ID.toString());
            assertThat(c.clientId()).isEqualTo(CLIENT.toString());
            assertThat(c.issuerOrgId()).isEqualTo(DRIVER.toString());
            assertThat(c.issuerOrgType()).isEqualTo("FREELANCER_ORG");
            assertThat(c.currency()).isEqualTo("XAF");
            assertThat(c.lines()).singleElement().satisfies(l ->
                    assertThat(l.lineTotal().amount().doubleValue()).isEqualTo(3000.0));
        });
        assertThat(invoicePaymentRefs).containsExactly("tx-42");

        // both parties can read it; a stranger cannot
        get(CLIENT).expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("PAID");
        get(DRIVER).expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("PAID");
        get(STRANGER).expectStatus().isForbidden();

        // paying twice is refused and does not hit the Kernel again
        int transfersBefore = countTo("/transfers");
        post(CLIENT, BASE + "/challenge", null).expectStatus().isEqualTo(409);
        assertThat(countTo("/transfers")).isEqualTo(transfersBefore);
    }

    @Test
    @DisplayName("a dispute hold on the paid delivery stops the driver from moving the received money out")
    void disputeHoldBlocksDriverWithdrawal() {
        fullPaymentFlow();
        holdService.holdSettlement("DELIVERY", DELIVERY_ID, "dispute").block();

        assertThat(holdStore.values()).singleElement().satisfies(h -> {
            assertThat(h.getOwnerUserId()).isEqualTo(DRIVER);
            assertThat(h.getStatus()).isEqualTo(HoldStatus.ACTIVE);
            assertThat(h.getAmount()).isEqualByComparingTo("3000.00");
        });
        assertThat(holdService.activeHeldAmount(DRIVER, null).block()).isEqualByComparingTo("3000.00");
        holdService.releaseHolds("DELIVERY", DELIVERY_ID).block();
        assertThat(holdService.activeHeldAmount(DRIVER, null).block()).isEqualByComparingTo("0");
    }

    // ── refusals ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("no receive code yet: the client is told to ask the driver to regenerate it (409), Kernel untouched")
    void noReceiveCode_409() {
        bearer.set(CLIENT);
        post(CLIENT, BASE + "/challenge", null).expectStatus().isEqualTo(409)
                .expectBody(String.class).value(b -> assertThat(b).contains("receive-code"));
        assertThat(countTo("/transfers")).isZero();
    }

    @Test
    @DisplayName("expired receive code is refused like a missing one")
    void expiredReceiveCode_409() {
        delivery.setDriverUserId(DRIVER);
        delivery.setReceiveCode("RC-OLD");
        delivery.setReceiveCodeExpiresAt(Instant.now().minusSeconds(5));
        bearer.set(CLIENT);
        post(CLIENT, BASE + "/challenge", null).expectStatus().isEqualTo(409);
    }

    @Test
    @DisplayName("only the client pays: the driver and strangers get 403; only the assigned driver generates a code")
    void roleChecks() {
        bearer.set(DRIVER);
        post(DRIVER, BASE + "/receive-code", null).expectStatus().isOk();
        post(DRIVER, BASE + "/challenge", null).expectStatus().isForbidden();
        post(STRANGER, BASE + "/challenge", null).expectStatus().isForbidden();
        post(STRANGER, BASE + "/receive-code", null).expectStatus().isForbidden();
        post(CLIENT, BASE + "/receive-code", null).expectStatus().isForbidden();
        assertThat(countTo("/transfers")).isZero();
    }

    @Test
    @DisplayName("wrong confirmation code: Kernel's 400 relayed with its message, no settlement recorded")
    void wrongConfirmationCode_relayed_nothingRecorded() {
        bearer.set(DRIVER);
        post(DRIVER, BASE + "/receive-code", null).expectStatus().isOk();
        bearer.set(CLIENT);
        post(CLIENT, BASE, "{\"challengeToken\":\"chal-1\",\"code\":\"000000\"}")
                .expectStatus().isBadRequest()
                .expectBody(String.class).value(b -> assertThat(b).contains("Invalid confirmation code"));
        assertThat(settlementStore).isEmpty();
        get(CLIENT).expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("NOT_SETTLED");
    }

    @Test
    @DisplayName("an invoicing failure never turns a successful payment into an error")
    void invoiceFailureDoesNotBreakPayment() {
        invoicingFails = true;
        bearer.set(DRIVER);
        post(DRIVER, BASE + "/receive-code", null).expectStatus().isOk();
        bearer.set(CLIENT);
        post(CLIENT, BASE, "{\"challengeToken\":\"chal-1\",\"code\":\"123456\"}")
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("PAID");
        assertThat(settlementStore).hasSize(1);
        assertThat(invoiceCommands).isEmpty();
    }

    @Test
    @DisplayName("a CASH delivery cannot be paid by wallet transfer (409)")
    void cashDeliveryRefused() {
        delivery.setAnnouncementId(null);
        delivery.setDeliveryNeedId(UUID.randomUUID());
        need = DeliveryNeed.builder().id(delivery.getDeliveryNeedId()).userId(CLIENT).paymentMethod("CASH").build();
        delivery.setDriverUserId(DRIVER);
        delivery.setReceiveCode("RC-777");
        delivery.setReceiveCodeExpiresAt(Instant.now().plusSeconds(600));
        bearer.set(CLIENT);
        post(CLIENT, BASE + "/challenge", null).expectStatus().isEqualTo(409);
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

        holdService = new WalletHoldService(holdRepo(), settlementRepo());
        KernelWalletService kernelWallet = new KernelWalletService(
                new KernelPaymentGatewayAdapter(kernelClient), holdService);
        SettlementService settlements = new SettlementService(settlementRepo());

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
                deliveries, announcements, needs, status, kernelWallet, settlements, tenantHolder, invoicing, users);

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

    private IWalletHoldRepository holdRepo() {
        return new IWalletHoldRepository() {
            @Override
            public Mono<WalletHold> save(WalletHold h) {
                holdStore.put(h.getId(), h.toBuilder().persisted(true).build());
                return Mono.just(holdStore.get(h.getId()));
            }

            @Override
            public Flux<WalletHold> findActiveByOwner(UUID owner) {
                return Flux.fromIterable(holdStore.values())
                        .filter(h -> h.getOwnerUserId().equals(owner) && h.getStatus() == HoldStatus.ACTIVE);
            }

            @Override
            public Flux<WalletHold> findActiveByReference(String type, UUID refId) {
                return Flux.fromIterable(holdStore.values())
                        .filter(h -> h.getReferenceType().equals(type) && h.getReferenceId().equals(refId)
                                && h.getStatus() == HoldStatus.ACTIVE);
            }
        };
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
