package com.yowyob.tiibntick.bootstrap.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.yowyob.tiibntick.common.session.AuthenticatedSessionPublisher;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.KernelPaymentGatewayAdapter;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletHoldUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.service.KernelWalletService;
import com.yowyob.tiibntick.core.billing.wallet.application.service.WalletAutoProvisioningListener;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IKernelAuthGatewayPort;
import com.yowyob.tiibntick.core.platformgateway.application.service.KernelAuthGatewayService;
import com.yowyob.tiibntick.core.platformgateway.domain.model.KernelApiEnvelope;
import com.yowyob.tiibntick.core.platformgateway.domain.model.KernelAuthResult;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End to end, with everything real except the Kernel (WireMock): the platform gateway's login
 * proxy → session publisher → wallet listener → {@code KernelWalletService} → real
 * {@code KernelPaymentGatewayAdapter} → the real {@code kernelPaymentWebClient} bean (bearer
 * filter included). Proves the wallet is looked up / created on the Kernel with the token the
 * user just obtained, and that the login itself is never affected.
 *
 * @author MANFOUO Braun
 */
@DisplayName("Wallet auto-provisioning — login proxy and profile creation, end to end against a fake Kernel")
class WalletAutoProvisioningEndToEndTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String WALLET_JSON = "{\"success\":true,\"data\":{\"id\":\"%s\",\"ownerId\":\"%s\","
            + "\"ownerType\":\"USER\",\"balance\":0}}";

    private WireMockServer kernel;
    private KernelAuthGatewayService gateway;
    private AuthenticatedSessionPublisher publisher;

    private static String userToken(String sub) {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + sub + "\"}").getBytes());
        return "eyJhbGciOiJSUzI1NiJ9." + payload + ".sig";
    }

    @BeforeEach
    void setUp() throws Exception {
        kernel = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        kernel.start();

        KernelBridgeConfig config = new KernelBridgeConfig();
        ReflectionTestUtils.setField(config, "kernelBaseUrl", "http://localhost:" + kernel.port());
        ReflectionTestUtils.setField(config, "kernelApiKey", "app-key");
        ReflectionTestUtils.setField(config, "kernelClientId", "app-client");
        ReflectionTestUtils.setField(config, "kernelConnectTimeoutMs", 2000);
        ReflectionTestUtils.setField(config, "kernelResponseTimeoutMs", 2000);
        WebClient paymentClient = config.kernelPaymentWebClient(WebClient.builder(),
                CircuitBreakerRegistry.ofDefaults(),
                TimeLimiterRegistry.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(10)).build()),
                RetryRegistry.ofDefaults());

        KernelWalletService wallets = new KernelWalletService(
                new KernelPaymentGatewayAdapter(paymentClient), mock(IWalletHoldUseCase.class));
        var listener = new WalletAutoProvisioningListener(wallets, new ObjectMapper(),
                Duration.ofHours(1), Duration.ofMinutes(5), Clock.systemUTC());
        publisher = new AuthenticatedSessionPublisher(() -> List.of(listener));

        IKernelAuthGatewayPort authPort = mock(IKernelAuthGatewayPort.class);
        when(authPort.invokeAuth(any(), anyString(), any(), any())).thenAnswer(inv -> {
            String path = inv.getArgument(1);
            String data = path.endsWith("/login")
                    ? "{\"accessToken\":\"" + userToken("user-A") + "\"}"
                    : "{\"nextStep\":\"CONFIRM_MFA\"}";
            return Mono.just(new KernelAuthResult(200,
                    new KernelApiEnvelope(true, MAPPER.readTree(data), "ok", null, "t")));
        });
        gateway = new KernelAuthGatewayService(authPort, publisher);
    }

    @AfterEach
    void tearDown() {
        kernel.stop();
    }

    private void awaitRequests(com.github.tomakehurst.wiremock.matching.RequestPatternBuilder pattern, int count)
            throws InterruptedException {
        for (int i = 0; i < 50 && kernel.findAll(pattern).size() < count; i++) {
            Thread.sleep(100);
        }
        assertThat(kernel.findAll(pattern)).hasSize(count);
    }

    @Test
    @DisplayName("login for a user with NO wallet: GET 404 then POST wallets/mine, both with the user's own token")
    void loginCreatesMissingWallet() throws Exception {
        String token = userToken("user-A");
        kernel.stubFor(get(urlEqualTo("/api/payments/wallets/mine"))
                .willReturn(aResponse().withStatus(404).withBody("{\"success\":false}")));
        kernel.stubFor(post(urlEqualTo("/api/payments/wallets/mine")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(WALLET_JSON.formatted(UUID.randomUUID(), UUID.randomUUID()))));

        StepVerifier.create(gateway.callAuth(HttpMethod.POST, "/api/auth/login", null, null))
                .assertNext(r -> assertThat(r.httpStatus()).isEqualTo(200)).verifyComplete();

        awaitRequests(postRequestedFor(urlEqualTo("/api/payments/wallets/mine")), 1);
        kernel.verify(getRequestedFor(urlEqualTo("/api/payments/wallets/mine"))
                .withHeader("Authorization", equalTo("Bearer " + token))
                .withHeader("X-Api-Key", equalTo("app-key")));
        kernel.verify(postRequestedFor(urlEqualTo("/api/payments/wallets/mine"))
                .withHeader("Authorization", equalTo("Bearer " + token)));
    }

    @Test
    @DisplayName("login for a user who already has a wallet: lookup only, nothing created; a second login within the TTL does not call the Kernel")
    void loginWithExistingWalletDoesNotCreate() throws Exception {
        kernel.stubFor(get(urlEqualTo("/api/payments/wallets/mine")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(WALLET_JSON.formatted(UUID.randomUUID(), UUID.randomUUID()))));

        gateway.callAuth(HttpMethod.POST, "/api/auth/login", null, null).block();
        awaitRequests(getRequestedFor(urlEqualTo("/api/payments/wallets/mine")), 1);
        gateway.callAuth(HttpMethod.POST, "/api/auth/login", null, null).block();
        Thread.sleep(400);

        assertThat(kernel.findAll(getRequestedFor(urlEqualTo("/api/payments/wallets/mine")))).hasSize(1);
        assertThat(kernel.findAll(postRequestedFor(urlEqualTo("/api/payments/wallets/mine")))).isEmpty();
    }

    @Test
    @DisplayName("the Kernel being down never breaks the login answer")
    void kernelDownDoesNotBreakLogin() throws Exception {
        kernel.stubFor(get(urlEqualTo("/api/payments/wallets/mine")).willReturn(aResponse().withStatus(503)));

        StepVerifier.create(gateway.callAuth(HttpMethod.POST, "/api/auth/login", null, null))
                .assertNext(r -> assertThat(r.httpStatus()).isEqualTo(200)).verifyComplete();

        awaitRequests(getRequestedFor(urlEqualTo("/api/payments/wallets/mine")), 1);
    }

    @Test
    @DisplayName("an MFA challenge (no token yet) touches no wallet")
    void mfaChallengeTouchesNothing() throws Exception {
        StepVerifier.create(gateway.callAuth(HttpMethod.POST, "/api/auth/select-context", null, null))
                .expectNextCount(1).verifyComplete();
        Thread.sleep(300);
        assertThat(kernel.getAllServeEvents()).isEmpty();
    }

    @Test
    @DisplayName("profile creation: the wallet is ensured with the caller's JWT taken from the request's security context")
    void profileCreationUsesCallerJwt() throws Exception {
        kernel.stubFor(get(urlEqualTo("/api/payments/wallets/mine"))
                .willReturn(aResponse().withStatus(404).withBody("{\"success\":false}")));
        kernel.stubFor(post(urlEqualTo("/api/payments/wallets/mine")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(WALLET_JSON.formatted(UUID.randomUUID(), UUID.randomUUID()))));
        Jwt jwt = Jwt.withTokenValue("caller-jwt-77").header("alg", "none").claim("sub", "u9")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        StepVerifier.create(publisher.profileCreated()
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt))))
                .verifyComplete();

        awaitRequests(postRequestedFor(urlEqualTo("/api/payments/wallets/mine")), 1);
        kernel.verify(postRequestedFor(urlEqualTo("/api/payments/wallets/mine"))
                .withHeader("Authorization", equalTo("Bearer caller-jwt-77")));
    }
}
