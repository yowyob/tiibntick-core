package com.yowyob.tiibntick.bootstrap.bridge;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link KernelBridgeConfig}'s WebClient beans actually enforce the
 * connect/response timeouts and the circuit-breaker/retry triplet described in
 * Chantier D · Audit n°6 · S4 ("Appels HTTP Kernel sans timeout, retry ni circuit
 * breaker").
 *
 * <p>Because the resilience behavior here is composed directly into an
 * {@link org.springframework.web.reactive.function.client.ExchangeFilterFunction} inside
 * the {@code @Bean} factory method (not via method-level {@code @CircuitBreaker}/
 * {@code @TimeLimiter} annotations, which only apply through Spring AOP on a
 * per-invocation basis and cannot decorate a bean-producer method itself — see the class
 * Javadoc), it is fully exercised by calling the bean-producer method directly against a
 * plain, non-Spring-managed {@link CircuitBreakerRegistry}/{@link TimeLimiterRegistry}/
 * {@link RetryRegistry}, with no Spring context required.
 *
 * @author MANFOUO Braun
 */
@DisplayName("KernelBridgeConfig — HTTP resilience (timeout / circuit breaker / retry)")
class KernelBridgeConfigResilienceTest {

    private static final String INSTANCE = "kernelWebClient";

    private WireMockServer wireMock;
    private KernelBridgeConfig config;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        config = new KernelBridgeConfig();
        ReflectionTestUtils.setField(config, "kernelBaseUrl", "http://localhost:" + wireMock.port());
        ReflectionTestUtils.setField(config, "kernelApiKey", "test-key");
        ReflectionTestUtils.setField(config, "kernelClientId", "test-client");
        ReflectionTestUtils.setField(config, "kernelConnectTimeoutMs", 2000);
        ReflectionTestUtils.setField(config, "kernelResponseTimeoutMs", 300); // short, for a fast test
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("response exceeding the configured response-timeout is aborted, not left hanging")
    void responseTimeoutAbortsSlowCall() {
        wireMock.stubFor(get(urlEqualTo("/slow"))
                .willReturn(aResponse().withFixedDelay(2000).withStatus(200)));

        WebClient client = config.kernelWebClient(
                WebClient.builder(),
                CircuitBreakerRegistry.ofDefaults(),
                permissiveTimeLimiter(),
                noRetry());

        StepVerifier.create(client.get().uri("/slow").retrieve().toBodilessEntity())
                .expectError()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("circuit breaker opens after repeated failures and short-circuits further calls")
    void circuitBreakerOpensAndShortCircuits() {
        wireMock.stubFor(get(urlEqualTo("/failing"))
                .willReturn(aResponse().withStatus(500)));

        // Tiny sliding window so 2 failures are enough to trip the breaker deterministically.
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .permittedNumberOfCallsInHalfOpenState(1)
                        .build());

        WebClient client = config.kernelWebClient(
                WebClient.builder(),
                circuitBreakerRegistry,
                permissiveTimeLimiter(),
                noRetry());

        // Two failing calls open the breaker...
        for (int i = 0; i < 2; i++) {
            StepVerifier.create(client.get().uri("/failing").retrieve().toBodilessEntity())
                    .expectError()
                    .verify(Duration.ofSeconds(2));
        }
        assertThat(circuitBreakerRegistry.circuitBreaker(INSTANCE).getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        wireMock.resetRequests();

        // ...and the next call is rejected locally, never reaching WireMock at all.
        StepVerifier.create(client.get().uri("/failing").retrieve().toBodilessEntity())
                .expectErrorMatches(ex -> ex instanceof CallNotPermittedException
                        || (ex.getCause() != null && ex.getCause() instanceof CallNotPermittedException))
                .verify(Duration.ofSeconds(2));
        assertThat(wireMock.getAllServeEvents()).isEmpty();
    }

    @Test
    @DisplayName("retry re-attempts on a transport-level failure and recovers on the next try")
    void retryReattemptsAndRecovers() {
        wireMock.stubFor(get(urlEqualTo("/flaky"))
                .inScenario("flaky")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
                .willSetStateTo("recovered"));
        wireMock.stubFor(get(urlEqualTo("/flaky"))
                .inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200)));

        RetryRegistry retryRegistry = RetryRegistry.of(
                RetryConfig.custom()
                        .maxAttempts(3)
                        .waitDuration(Duration.ofMillis(50))
                        .retryOnException(ex -> true) // any transport failure, for this test
                        .build());

        WebClient client = config.kernelWebClient(
                WebClient.builder(),
                CircuitBreakerRegistry.ofDefaults(),
                permissiveTimeLimiter(),
                retryRegistry);

        StepVerifier.create(client.get().uri("/flaky").retrieve().toBodilessEntity())
                .expectNextCount(1)
                .verifyComplete();

        assertThat(wireMock.getAllServeEvents()).hasSize(2); // first attempt failed, retry succeeded
    }

    private static TimeLimiterRegistry permissiveTimeLimiter() {
        return TimeLimiterRegistry.of(
                TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(10)).build());
    }

    private static RetryRegistry noRetry() {
        return RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build());
    }
}
