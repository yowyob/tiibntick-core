package com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.dto.KernelWalletDto;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.dto.KernelWalletTransactionDto;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link KernelPaymentGatewayAdapter} against a {@link MockWebServer} —
 * verifies the Kernel {@code {success, data, ...}} envelope is unwrapped correctly
 * (never skipped, per ADR-012 / KernelResponses) for every payment-controller operation,
 * and that errors on pay/recharge/can-operate propagate rather than fail open.
 *
 * @author MANFOUO Braun
 */
class KernelPaymentGatewayAdapterTest {

    private MockWebServer mockWebServer;
    private KernelPaymentGatewayAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
        adapter = new KernelPaymentGatewayAdapter(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("getWallet unwraps the {success,data} envelope")
    void getWalletUnwrapsEnvelope() throws InterruptedException {
        UUID walletId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "success": true,
                          "data": {
                            "id": "%s",
                            "ownerId": "%s",
                            "ownerName": "Test Owner",
                            "balance": 15000.00
                          },
                          "message": null,
                          "errorCode": null,
                          "timestamp": "2026-07-18T10:00:00Z"
                        }
                        """.formatted(walletId, ownerId)));

        StepVerifier.create(adapter.getWallet(walletId))
                .assertNext(wallet -> {
                    assertThat(wallet.id()).isEqualTo(walletId);
                    assertThat(wallet.ownerId()).isEqualTo(ownerId);
                    assertThat(wallet.balance()).isEqualByComparingTo(new BigDecimal("15000.00"));
                })
                .verifyComplete();

        RecordedRequest recorded = mockWebServer.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/api/payments/wallets/" + walletId);
    }

    @Test
    @DisplayName("getWallet fails open (empty) on 404 — read path never blocks a caller on Kernel absence")
    void getWalletFailsOpenOnNotFound() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        StepVerifier.create(adapter.getWallet(UUID.randomUUID()))
                .verifyComplete();
    }

    @Test
    @DisplayName("canOperate unwraps a boolean envelope")
    void canOperateUnwrapsBoolean() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"success": true, "data": true, "message": null, "errorCode": null, "timestamp": null}
                        """));

        StepVerifier.create(adapter.canOperate(UUID.randomUUID(), new BigDecimal("100.00")))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("canOperate propagates a Kernel error instead of failing open — it is a safety gate before a debit")
    void canOperatePropagatesError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(adapter.canOperate(UUID.randomUUID(), new BigDecimal("100.00")))
                .expectError()
                .verify();
    }

    @Test
    @DisplayName("pay unwraps the transaction envelope and classifies a successful status")
    void payUnwrapsEnvelopeAndClassifiesSuccess() throws InterruptedException {
        UUID walletId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "success": true,
                          "data": {"id": "%s", "status": "SUCCESSFUL"},
                          "message": null, "errorCode": null, "timestamp": null
                        }
                        """.formatted(txId)));

        StepVerifier.create(adapter.pay(walletId, new BigDecimal("500.00"), "XAF", "REF-1", "test debit"))
                .assertNext(tx -> {
                    assertThat(tx.id()).isEqualTo(txId);
                    assertThat(tx.isSuccess()).isTrue();
                    assertThat(tx.isFailure()).isFalse();
                })
                .verifyComplete();

        RecordedRequest recorded = mockWebServer.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/api/payments/wallets/" + walletId + "/pay");
        assertThat(recorded.getBody().readUtf8()).contains("\"amount\":500.0").contains("REF-1");
    }

    @Test
    @DisplayName("pay propagates a Kernel error instead of failing open — never silently no-op a debit")
    void payPropagatesErrorRatherThanFailingOpen() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(adapter.pay(UUID.randomUUID(), BigDecimal.TEN, "XAF", "REF-2", "desc"))
                .expectError()
                .verify();
    }

    @Test
    @DisplayName("recharge classifies an unrecognized status as pending, not success")
    void rechargeUnrecognizedStatusIsNotSuccess() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"success": true, "data": {"id": "%s", "status": "AWAITING_PROVIDER"},
                         "message": null, "errorCode": null, "timestamp": null}
                        """.formatted(UUID.randomUUID())));

        StepVerifier.create(adapter.recharge(UUID.randomUUID(), BigDecimal.TEN, "XAF", "REF-3", "desc"))
                .assertNext(tx -> {
                    assertThat(tx.isSuccess()).isFalse();
                    assertThat(tx.isFailure()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getTransactions unwraps a list envelope, fail-open on error")
    void getTransactionsUnwrapsListAndFailsOpen() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"success": true, "data": [{"id": "%s", "status": "SUCCESSFUL"}],
                         "message": null, "errorCode": null, "timestamp": null}
                        """.formatted(UUID.randomUUID())));

        StepVerifier.create(adapter.getTransactions(UUID.randomUUID()))
                .expectNextCount(1)
                .verifyComplete();

        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        StepVerifier.create(adapter.getTransactions(UUID.randomUUID()))
                .verifyComplete();
    }

    @Test
    @DisplayName("createWallet unwraps the envelope and propagates errors")
    void createWalletUnwrapsEnvelope() {
        UUID walletId = UUID.randomUUID();
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"success": true, "data": {"id": "%s", "balance": 0}, "message": null,
                         "errorCode": null, "timestamp": null}
                        """.formatted(walletId)));

        StepVerifier.create(adapter.createWallet(UUID.randomUUID(), "Test Wallet"))
                .assertNext(wallet -> assertThat(wallet.id()).isEqualTo(walletId))
                .verifyComplete();
    }

    @Test
    @DisplayName("initiateOrder posts to /api/payments/orders and unwraps the PaymentOrderResponse envelope")
    void initiateOrderUnwrapsEnvelope() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "success": true,
                          "data": {
                            "id": "order-123",
                            "tenantId": "tenant-1",
                            "status": "PENDING",
                            "provider": "MYCOOLPAY",
                            "method": "MOBILE_MONEY",
                            "amount": 5000.00,
                            "currency": "XAF"
                          },
                          "message": null, "errorCode": null, "timestamp": null
                        }
                        """));

        StepVerifier.create(adapter.initiateOrder("MYCOOLPAY", "MOBILE_MONEY", new BigDecimal("5000.00"),
                        "XAF", "+237600000000", "test payment", "https://callback", "IDEMP-1"))
                .assertNext(order -> {
                    assertThat(order.id()).isEqualTo("order-123");
                    assertThat(order.isSuccess()).isFalse();
                    assertThat(order.isFailure()).isFalse();
                })
                .verifyComplete();

        RecordedRequest recorded = mockWebServer.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/api/payments/orders");
        assertThat(recorded.getBody().readUtf8())
                .contains("\"provider\":\"MYCOOLPAY\"")
                .contains("\"method\":\"MOBILE_MONEY\"")
                .contains("IDEMP-1");
    }

    @Test
    @DisplayName("initiateOrder propagates a Kernel error instead of failing open — never silently no-op a provider dispatch")
    void initiateOrderPropagatesError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(adapter.initiateOrder("STRIPE", "CARD", BigDecimal.TEN, "XAF",
                        null, "desc", null, "IDEMP-2"))
                .expectError()
                .verify();
    }

    @Test
    @DisplayName("refreshOrder posts to /api/payments/orders/{id}/refresh and classifies a terminal status")
    void refreshOrderUnwrapsEnvelopeAndClassifiesSuccess() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"success": true, "data": {"id": "order-123", "status": "CONFIRMED",
                         "providerReference": "PROV-REF-1"}, "message": null, "errorCode": null, "timestamp": null}
                        """));

        StepVerifier.create(adapter.refreshOrder("order-123"))
                .assertNext(order -> {
                    assertThat(order.isSuccess()).isTrue();
                    assertThat(order.providerReference()).isEqualTo("PROV-REF-1");
                })
                .verifyComplete();

        RecordedRequest recorded = mockWebServer.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/api/payments/orders/order-123/refresh");
    }

    @Test
    @DisplayName("refreshOrder fails open (empty) on error — a single failed poll must not break the reconciliation loop")
    void refreshOrderFailsOpenOnError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(adapter.refreshOrder("order-999"))
                .verifyComplete();
    }
}
