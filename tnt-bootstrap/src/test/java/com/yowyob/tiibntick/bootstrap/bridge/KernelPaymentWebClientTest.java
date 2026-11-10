package com.yowyob.tiibntick.bootstrap.bridge;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the real {@link KernelBridgeConfig#kernelPaymentWebClient} bean (Kernel wallet
 * migration): the end user's bearer token is forwarded to the Kernel inside a request, API-key
 * identity is always sent, scheduler-style calls (no JWT in context) carry no Authorization
 * header, and a money-moving POST is never replayed — the configured retry instance for
 * {@code kernelPaymentWebClient} in {@code application.yml} allows a single attempt.
 *
 * @author MANFOUO Braun
 */
@DisplayName("KernelBridgeConfig — kernelPaymentWebClient (bearer forwarding, no replay of money POSTs)")
class KernelPaymentWebClientTest {

    private WireMockServer wireMock;
    private WebClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        KernelBridgeConfig config = new KernelBridgeConfig();
        ReflectionTestUtils.setField(config, "kernelBaseUrl", "http://localhost:" + wireMock.port());
        ReflectionTestUtils.setField(config, "kernelApiKey", "test-key");
        ReflectionTestUtils.setField(config, "kernelClientId", "test-client");
        ReflectionTestUtils.setField(config, "kernelConnectTimeoutMs", 2000);
        ReflectionTestUtils.setField(config, "kernelResponseTimeoutMs", 2000);

        client = config.kernelPaymentWebClient(
                WebClient.builder(),
                CircuitBreakerRegistry.ofDefaults(),
                TimeLimiterRegistry.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(10)).build()),
                RetryRegistry.ofDefaults());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("inside a request, the caller's JWT is forwarded as Authorization: Bearer, with API-key identity")
    void forwardsCallerBearer() {
        wireMock.stubFor(get(urlEqualTo("/api/payments/wallets/mine"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        Jwt jwt = Jwt.withTokenValue("user-jwt-123").header("alg", "none")
                .claim("sub", "u1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        StepVerifier.create(client.get().uri("/api/payments/wallets/mine").retrieve().toBodilessEntity()
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt))))
                .expectNextCount(1).verifyComplete();

        wireMock.verify(getRequestedFor(urlEqualTo("/api/payments/wallets/mine"))
                .withHeader("Authorization", equalTo("Bearer user-jwt-123"))
                .withHeader("X-Api-Key", equalTo("test-key"))
                .withHeader("X-Client-Id", equalTo("test-client"))
                .withHeader("X-Solution-Code", equalTo("TNT")));
    }

    @Test
    @DisplayName("without a JWT in context (scheduler/Kafka) no Authorization header is sent")
    void noBearerOutsideRequest() {
        wireMock.stubFor(post(urlEqualTo("/api/payments/orders/o1/refresh"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        StepVerifier.create(client.post().uri("/api/payments/orders/o1/refresh").retrieve().toBodilessEntity())
                .expectNextCount(1).verifyComplete();

        wireMock.verify(postRequestedFor(urlEqualTo("/api/payments/orders/o1/refresh"))
                .withHeader("Authorization", absent())
                .withHeader("X-Api-Key", equalTo("test-key")));
    }

    @Test
    @DisplayName("application.yml gives kernelPaymentWebClient a single retry attempt (a money POST is never replayed)")
    void ymlDisablesRetryForPaymentClient() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties props = yaml.getObject();

        assertThat(props).isNotNull();
        assertThat(props.getProperty("resilience4j.retry.instances.kernelPaymentWebClient.max-attempts"))
                .isEqualTo("1");
        assertThat(props.getProperty("resilience4j.circuitbreaker.instances.kernelPaymentWebClient.sliding-window-size"))
                .isNotNull();
        assertThat(props.getProperty("resilience4j.timelimiter.instances.kernelPaymentWebClient.timeout-duration"))
                .isNotNull();
    }

    @Test
    @DisplayName("with max-attempts=1 (as configured), a connection reset on a POST yields exactly one request")
    void postIsNotReplayedOnTransportFailure() {
        wireMock.stubFor(post(urlEqualTo("/api/payments/wallets/w1/transfers"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        KernelBridgeConfig config = new KernelBridgeConfig();
        ReflectionTestUtils.setField(config, "kernelBaseUrl", "http://localhost:" + wireMock.port());
        ReflectionTestUtils.setField(config, "kernelApiKey", "k");
        ReflectionTestUtils.setField(config, "kernelClientId", "c");
        ReflectionTestUtils.setField(config, "kernelConnectTimeoutMs", 2000);
        ReflectionTestUtils.setField(config, "kernelResponseTimeoutMs", 2000);
        RetryRegistry singleAttempt = RetryRegistry.of(
                io.github.resilience4j.retry.RetryConfig.custom().maxAttempts(1).build());
        WebClient strict = config.kernelPaymentWebClient(
                WebClient.builder(), CircuitBreakerRegistry.ofDefaults(),
                TimeLimiterRegistry.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(10)).build()),
                singleAttempt);

        StepVerifier.create(strict.post().uri("/api/payments/wallets/w1/transfers")
                        .bodyValue(Map.of("amount", 1)).retrieve().toBodilessEntity())
                .expectError().verify(Duration.ofSeconds(5));

        assertThat(wireMock.getAllServeEvents()).hasSize(1);
    }
}
