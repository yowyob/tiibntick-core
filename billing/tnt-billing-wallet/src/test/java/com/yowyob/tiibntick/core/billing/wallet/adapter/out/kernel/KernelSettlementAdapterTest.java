package com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.CompensationRequest;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.EscrowHoldRequest;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Share;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L5: the Kernel settlement contract (docs/kernel-api/openapi.json) over HTTP — paths, bodies, envelope, and which
 * identity calls: the payer's code operations with the user client, the platform operations with the service account.
 */
class KernelSettlementAdapterTest {

    private MockWebServer kernel;
    private KernelSettlementAdapter adapter;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void start() throws Exception {
        kernel = new MockWebServer();
        kernel.start();
        String base = kernel.url("/").toString();
        adapter = new KernelSettlementAdapter(
                WebClient.builder().baseUrl(base).defaultHeader("X-Identity", "user").build(),
                WebClient.builder().baseUrl(base).defaultHeader("X-Identity", "system").build());
    }

    @AfterEach
    void stop() throws Exception {
        kernel.shutdown();
    }

    private void answer(String data) {
        kernel.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"success\":true,\"data\":" + data + "}"));
    }

    private RecordedRequest taken() throws InterruptedException {
        return kernel.takeRequest(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("escrow challenge then hold go with the payer's identity; the hold carries token and code")
    void escrowWithPayerCode() throws Exception {
        UUID subject = UUID.randomUUID();
        UUID payer = UUID.randomUUID();
        EscrowHoldRequest req = new EscrowHoldRequest("DELIVERY", subject, payer, "XAF",
                List.of(new Share(Share.PLATFORM, UUID.randomUUID(), new BigDecimal("50")),
                        new Share(Share.COURIER, UUID.randomUUID(), new BigDecimal("950"))), null, null, null);
        answer("{\"challengeToken\":\"tok\",\"expiresInSeconds\":300,\"codePreview\":\"**12\"}");
        assertThat(adapter.escrowChallenge(req).block().challengeToken()).isEqualTo("tok");
        RecordedRequest challenge = taken();
        assertThat(challenge.getPath()).isEqualTo("/api/payments/escrows/challenge");
        assertThat(challenge.getHeader("X-Identity")).isEqualTo("user");
        JsonNode body = json.readTree(challenge.getBody().readUtf8());
        assertThat(body.has("code")).isFalse();
        assertThat(body.path("shares")).hasSize(2);
        assertThat(body.path("shares").get(0).path("role").asText()).isEqualTo("PLATFORM");

        UUID escrow = UUID.randomUUID();
        answer("{\"id\":\"" + escrow + "\",\"status\":\"HELD\",\"amount\":1000,\"subjectId\":\"" + subject + "\"}");
        assertThat(adapter.holdEscrow(req.withCode("tok", "123456")).block().status()).isEqualTo("HELD");
        RecordedRequest hold = taken();
        assertThat(hold.getPath()).isEqualTo("/api/payments/escrows");
        assertThat(hold.getHeader("X-Identity")).isEqualTo("user");
        JsonNode held = json.readTree(hold.getBody().readUtf8());
        assertThat(held.path("challengeToken").asText()).isEqualTo("tok");
        assertThat(held.path("code").asText()).isEqualTo("123456");
    }

    @Test
    @DisplayName("platform escrow operations use the service account; a missing escrow is empty, a refusal propagates")
    void platformEscrowOperations() throws Exception {
        UUID escrow = UUID.randomUUID();
        kernel.enqueue(new MockResponse().setResponseCode(404));
        assertThat(adapter.findEscrow("DELIVERY", UUID.randomUUID()).block()).isNull();
        RecordedRequest find = taken();
        assertThat(find.getPath()).startsWith("/api/payments/escrows?subjectType=DELIVERY&subjectId=");
        assertThat(find.getHeader("X-Identity")).isEqualTo("system");

        answer("{\"id\":\"" + escrow + "\",\"status\":\"FROZEN\"}");
        assertThat(adapter.freezeEscrow(escrow, "dispute").block().status()).isEqualTo("FROZEN");
        RecordedRequest freeze = taken();
        assertThat(freeze.getPath()).isEqualTo("/api/payments/escrows/" + escrow + "/freeze");
        assertThat(json.readTree(freeze.getBody().readUtf8()).path("reason").asText()).isEqualTo("dispute");
        assertThat(freeze.getHeader("X-Identity")).isEqualTo("system");

        answer("{\"id\":\"" + escrow + "\",\"status\":\"RELEASED\"}");
        assertThat(adapter.releaseEscrow(escrow).block().status()).isEqualTo("RELEASED");
        assertThat(taken().getPath()).isEqualTo("/api/payments/escrows/" + escrow + "/release");

        kernel.enqueue(new MockResponse().setResponseCode(409).setBody("{\"success\":false}"));
        assertThatThrownBy(() -> adapter.refundEscrow(escrow, "x").block())
                .isInstanceOf(WebClientResponseException.Conflict.class);
    }

    @Test
    @DisplayName("fund, compensation and wallet lookup: platform identity, reference DISPUTE:<id>")
    void compensationFund() throws Exception {
        answer("{\"walletId\":\"" + UUID.randomUUID() + "\",\"balance\":250000}");
        assertThat(adapter.compensationFund().block().balance()).isEqualByComparingTo("250000");
        assertThat(taken().getPath()).isEqualTo("/api/payments/compensation-fund");

        UUID wallet = UUID.randomUUID();
        answer("{\"id\":\"" + UUID.randomUUID() + "\",\"reference\":\"DISPUTE:d1\",\"beneficiaryWalletId\":\""
                + wallet + "\",\"amount\":15000}");
        assertThat(adapter.compensate(new CompensationRequest(wallet, new BigDecimal("15000"), "DISPUTE:d1"))
                .block().reference()).isEqualTo("DISPUTE:d1");
        RecordedRequest pay = taken();
        assertThat(pay.getPath()).isEqualTo("/api/payments/compensations");
        assertThat(pay.getHeader("X-Identity")).isEqualTo("system");
        JsonNode body = json.readTree(pay.getBody().readUtf8());
        assertThat(body.path("beneficiaryWalletId").asText()).isEqualTo(wallet.toString());
        assertThat(body.path("reference").asText()).isEqualTo("DISPUTE:d1");

        UUID owner = UUID.randomUUID();
        kernel.enqueue(new MockResponse().setResponseCode(404));
        assertThat(adapter.walletOfOwner(owner).block()).isNull();
        assertThat(taken().getPath()).isEqualTo("/api/payments/wallets/owner/" + owner);
    }

    @Test
    @DisplayName("cash collection: opened and cancelled by the platform, remitted by the courier with his code")
    void cashCollection() throws Exception {
        UUID id = UUID.randomUUID();
        answer("{\"id\":\"" + id + "\",\"status\":\"OPEN\",\"expectedAmount\":1000}");
        adapter.openCashCollection(new com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel
                .KernelSettlementDtos.CashCollectionRequest("DELIVERY", UUID.randomUUID(), UUID.randomUUID(), "XAF",
                List.of())).block();
        RecordedRequest open = taken();
        assertThat(open.getPath()).isEqualTo("/api/payments/cash-collections");
        assertThat(open.getHeader("X-Identity")).isEqualTo("system");

        answer("{\"challengeToken\":\"t\",\"expiresInSeconds\":300}");
        adapter.remitChallenge(id).block();
        RecordedRequest ch = taken();
        assertThat(ch.getPath()).isEqualTo("/api/payments/cash-collections/" + id + "/remit/challenge");
        assertThat(ch.getHeader("X-Identity")).isEqualTo("user");

        answer("{\"id\":\"" + id + "\",\"status\":\"REMITTED\"}");
        assertThat(adapter.remit(id, "t", "654321").block().status()).isEqualTo("REMITTED");
        RecordedRequest remit = taken();
        assertThat(json.readTree(remit.getBody().readUtf8()).path("code").asText()).isEqualTo("654321");
        assertThat(remit.getHeader("X-Identity")).isEqualTo("user");

        answer("{\"id\":\"" + id + "\",\"status\":\"COLLECTED\",\"collectedAmount\":1000}");
        adapter.markCollected(id, new BigDecimal("1000")).block();
        RecordedRequest collected = taken();
        assertThat(collected.getPath()).isEqualTo("/api/payments/cash-collections/" + id + "/collected");
        assertThat(json.readTree(collected.getBody().readUtf8()).path("amount").decimalValue())
                .isEqualByComparingTo("1000");
    }
}
