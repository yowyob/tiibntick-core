package com.yowyob.tiibntick.core.trust.adapter.out.rest;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TrustEventRestClientAdapter}.
 *
 * <p>Uses WireMock to simulate the {@code yow-trust-event} REST API.
 * Tests the reactive WebClient integration: routing, response parsing,
 * and error fallback behavior.
 *
 * @author MANFOUO Braun
 */
@DisplayName("TrustEventRestClientAdapter — REST Client Tests")
class TrustEventRestClientAdapterTest {

    private static WireMockServer wireMock;
    private TrustEventRestClientAdapter adapter;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        final WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .build();
        adapter = new TrustEventRestClientAdapter(webClient);
    }

    // ── findTxHashByEntityId ──────────────────────────────────────────────────

    @Test
    @DisplayName("findTxHashByEntityId() should return txHash when COMMITTED event found")
    void shouldReturnTxHashWhenFound() {
        final String txHash = "b".repeat(64);

        wireMock.stubFor(get(urlPathEqualTo("/kernel/trust/events/entity/DELIVERY_PROOF/proof-001"))
                .withQueryParam("tenantId", equalTo("tenant-001"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"evt-001\",\"entityType\":\"DELIVERY_PROOF\"," +
                                "\"entityId\":\"proof-001\",\"status\":\"COMMITTED\"," +
                                "\"txHash\":\"" + txHash + "\",\"occurredAt\":\"2025-01-01T12:00:00\"}]")));

        StepVerifier.create(adapter.findTxHashByEntityId("proof-001", "DELIVERY_PROOF", "tenant-001"))
                .assertNext(found -> assertThat(found).isEqualTo(txHash))
                .verifyComplete();
    }

    @Test
    @DisplayName("findTxHashByEntityId() should return empty when 404")
    void shouldReturnEmptyWhenNotFound() {
        wireMock.stubFor(get(urlPathMatching("/kernel/trust/events/entity/.*"))
                .willReturn(aResponse().withStatus(404)));

        StepVerifier.create(adapter.findTxHashByEntityId("unknown", "DELIVERY_PROOF", "tenant-001"))
                .verifyComplete();
    }

    // ── verifyProof ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("verifyProof() should return true when proof is valid")
    void shouldReturnTrueWhenValid() {
        wireMock.stubFor(get(urlPathEqualTo("/kernel/trust/events/verify"))
                .withQueryParam("txHash", equalTo("b".repeat(64)))
                .withQueryParam("expectedHash", equalTo("a".repeat(64)))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"txHash\":\"" + "b".repeat(64) + "\"," +
                                "\"expectedHash\":\"" + "a".repeat(64) + "\"," +
                                "\"valid\":true,\"verifiedAt\":\"2025-01-01T12:00:00\"}")));

        StepVerifier.create(adapter.verifyProof("b".repeat(64), "a".repeat(64)))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("verifyProof() should return false when proof is invalid")
    void shouldReturnFalseWhenInvalid() {
        wireMock.stubFor(get(urlPathEqualTo("/kernel/trust/events/verify"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"valid\":false}")));

        StepVerifier.create(adapter.verifyProof("tx", "hash"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("verifyProof() propagates a server error on the raw (non-Spring-proxied) call — "
            + "the trustEventGatewayRead circuit breaker's fallback is what degrades this to "
            + "false in a real deployment, applied by AOP around the Spring-managed bean, "
            + "which this plain WireMock-backed unit test deliberately bypasses")
    void shouldPropagateErrorOnServerError() {
        wireMock.stubFor(get(urlPathEqualTo("/kernel/trust/events/verify"))
                .willReturn(aResponse().withStatus(500)));

        StepVerifier.create(adapter.verifyProof("tx", "hash"))
                .expectError()
                .verify();
    }
}
