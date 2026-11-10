package com.yowyob.tiibntick.core.billing.wallet.adapter.in.web;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.KernelPaymentGatewayAdapter;
import com.yowyob.tiibntick.core.billing.wallet.application.service.KernelWalletService;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration test of the whole Kernel-wallet chain —
 * {@code KernelWalletController -> KernelWalletService -> KernelPaymentGatewayAdapter -> HTTP} —
 * against a {@link MockWebServer} that plays the Kernel (envelope {@code {success,data}}, exact
 * paths and bodies of the published OpenAPI). The real {@code @RequirePermission} aspect and the
 * real {@link WalletExceptionHandler} are in the loop.
 *
 * <p>What it proves: the wallet id is always resolved from {@code /wallets/mine} (never from the
 * client), the two-step transfer sends {@code challengeToken} + {@code code} to the right Kernel
 * path with the right body, Kernel 4xx errors are relayed with their status, and the endpoints
 * are permission-guarded.
 *
 * @author MANFOUO Braun
 */
class KernelWalletFlowIntegrationTest {

    private static final UUID WALLET_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();

    private MockWebServer kernel;
    private final List<RecordedRequest> seen = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        kernel = new MockWebServer();
        kernel.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                seen.add(request);
                String path = request.getPath();
                String body = request.getBody().clone().readUtf8();
                if (path.equals("/api/payments/wallets/mine") && request.getMethod().equals("GET")) {
                    return json(200, """
                            {"success":true,"data":{"id":"%s","ownerId":"%s","ownerType":"USER",
                             "ownerName":"Test","balance":12500.00}}""".formatted(WALLET_ID, OWNER_ID));
                }
                if (path.equals("/api/payments/wallets/" + WALLET_ID + "/transfers/challenge")) {
                    return json(200, """
                            {"success":true,"data":{"challengeToken":"chal-1","expiresInSeconds":300,
                             "codePreview":"***123"}}""");
                }
                if (path.equals("/api/payments/wallets/" + WALLET_ID + "/transfers")) {
                    if (body.contains("\"code\":\"000000\"")) {
                        return json(400, """
                                {"success":false,"message":"Invalid confirmation code","errorCode":"BAD_CODE"}""");
                    }
                    return json(200, """
                            {"success":true,"data":{"id":"%s","status":"COMPLETED"}}""".formatted(UUID.randomUUID()));
                }
                if (path.equals("/api/payments/wallets/" + WALLET_ID + "/receive-codes")) {
                    return json(200, """
                            {"success":true,"data":{"code":"AB12CD","expiresAt":"2026-11-10T12:00:00Z"}}""");
                }
                if (path.equals("/api/payments/wallets/" + WALLET_ID + "/recharge")) {
                    return json(200, """
                            {"success":true,"data":{"orderId":"%s","walletId":"%s","status":"PENDING_PAYMENT",
                             "amount":5000,"currency":"XAF"}}""".formatted(UUID.randomUUID(), WALLET_ID));
                }
                if (path.equals("/api/payments/withdrawals/availability")) {
                    return json(200, "{\"success\":true,\"data\":{\"available\":true}}");
                }
                return json(404, "{\"success\":false,\"message\":\"not mocked: " + path + "\"}");
            }
        });
        kernel.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        kernel.shutdown();
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse().setResponseCode(code)
                .addHeader("Content-Type", "application/json").setBody(body);
    }

    @Test
    @DisplayName("GET /me returns the Kernel wallet of the caller (ownerType included)")
    void myWallet() {
        client("wallet:read").get().uri("/billing/kernel-wallet/me").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(WALLET_ID.toString())
                .jsonPath("$.ownerType").isEqualTo("USER")
                .jsonPath("$.balance").isEqualTo(12500.00);
    }

    @Test
    @DisplayName("two-step transfer: challenge then confirm hit the Kernel with the resolved wallet id and the code")
    void transferTwoStepFlow() {
        WebTestClient c = client("payment:process", "wallet:read");

        c.post().uri("/billing/kernel-wallet/me/transfers/challenge")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"recipientCode\":\"AB12CD\",\"amount\":4750,\"reference\":\"DELIVERY-1\"}")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.challengeToken").isEqualTo("chal-1");

        c.post().uri("/billing/kernel-wallet/me/transfers")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"recipientCode":"AB12CD","amount":4750,"reference":"DELIVERY-1",
                         "challengeToken":"chal-1","code":"123456"}""")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("COMPLETED");

        RecordedRequest confirm = seen.stream()
                .filter(r -> r.getPath().equals("/api/payments/wallets/" + WALLET_ID + "/transfers"))
                .findFirst().orElseThrow();
        String sent = confirm.getBody().readUtf8();
        assertThat(sent).contains("\"recipientCode\":\"AB12CD\"", "\"challengeToken\":\"chal-1\"",
                "\"code\":\"123456\"", "\"reference\":\"DELIVERY-1\"");
        // the wallet id comes from /wallets/mine, resolved once per operation, before the transfer
        assertThat(seen.stream().map(RecordedRequest::getPath).toList())
                .contains("/api/payments/wallets/mine");
    }

    @Test
    @DisplayName("a Kernel 400 (wrong confirmation code) is relayed as HTTP 400 with the Kernel message")
    void kernelClientErrorIsRelayed() {
        client("payment:process", "wallet:read").post().uri("/billing/kernel-wallet/me/transfers")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"recipientCode":"AB12CD","amount":4750,"reference":"R",
                         "challengeToken":"chal-1","code":"000000"}""")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("Invalid confirmation code")
                        .contains("kernel-wallet-error")
                        .containsPattern("\"kernelStatus\"\\s*:\\s*400"));
    }

    @Test
    @DisplayName("receive-code, recharge and withdrawal availability are wired to the right Kernel paths")
    void otherOperations() {
        WebTestClient c = client("payment:process", "wallet:read", "wallet:write");

        c.post().uri("/billing/kernel-wallet/me/receive-codes").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.code").isEqualTo("AB12CD");

        c.post().uri("/billing/kernel-wallet/me/recharge")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"amount":5000,"currency":"XAF","provider":"MYCOOLPAY","method":"MOBILE_MONEY",
                         "payerReference":"670000000","idempotencyKey":"idem-1"}""")
                .exchange().expectStatus().isAccepted()
                .expectBody().jsonPath("$.status").isEqualTo("PENDING_PAYMENT");

        c.get().uri("/billing/kernel-wallet/withdrawals/availability").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.available").isEqualTo(true);

        RecordedRequest recharge = seen.stream()
                .filter(r -> r.getPath().endsWith("/recharge")).findFirst().orElseThrow();
        assertThat(recharge.getBody().readUtf8())
                .contains("\"provider\":\"MYCOOLPAY\"", "\"method\":\"MOBILE_MONEY\"",
                        "\"idempotencyKey\":\"idem-1\"", "\"payerReference\":\"670000000\"");
    }

    @Test
    @DisplayName("money movements are 403 without payment:process and never reach the Kernel")
    void moneyMovementIsPermissionGuarded() {
        client("wallet:read").post().uri("/billing/kernel-wallet/me/transfers/challenge")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"recipientCode\":\"AB12CD\",\"amount\":4750}")
                .exchange().expectStatus().isForbidden();

        assertThat(seen).isEmpty();
    }

    @Test
    @DisplayName("invalid bodies are rejected before any Kernel call")
    void invalidBodyRejected() {
        client("payment:process").post().uri("/billing/kernel-wallet/me/transfers")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"recipientCode\":\"AB12CD\",\"amount\":-5}")
                .exchange().expectStatus().isBadRequest();

        assertThat(seen).isEmpty();
    }

    // ── harness ────────────────────────────────────────────────────────────────

    private WebTestClient client(String... authorities) {
        var auth = new TestingAuthenticationToken("user", "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        auth.setAuthenticated(true);

        // Same codec setup as production (TntCoreConfig#webClientBuilder / TntWebFluxConfig): the
        // application DTOs use com.fasterxml JsonNode, which needs the Jackson 2 codecs.
        JsonMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .visibility(PropertyAccessor.FIELD, Visibility.ANY)
                .visibility(PropertyAccessor.GETTER, Visibility.NONE)
                .visibility(PropertyAccessor.IS_GETTER, Visibility.NONE)
                .build();
        WebClient webClient = WebClient.builder()
                .exchangeStrategies(ExchangeStrategies.builder().codecs(c -> {
                    c.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(mapper));
                    c.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper));
                }).build())
                .baseUrl(kernel.url("/").toString()).build();
        KernelWalletService service = new KernelWalletService(new KernelPaymentGatewayAdapter(webClient));

        TntPermissionEvaluator evaluator = new TntPermissionEvaluator(
                mock(ReactivePermissionResolver.class), new TntRoleDefinitionRegistry());
        AspectJProxyFactory factory = new AspectJProxyFactory(new KernelWalletController(service));
        factory.setProxyTargetClass(true);
        factory.addAspect(new TntPermissionAspect(evaluator));
        KernelWalletController proxied = factory.getProxy();

        return WebTestClient.bindToController(proxied)
                .httpMessageCodecs(c -> {
                    c.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(mapper));
                    c.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper));
                })
                .controllerAdvice(new TntRoleExceptionHandler(), new WalletExceptionHandler())
                .webFilter((exchange, chain) -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .build();
    }
}
