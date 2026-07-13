package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationPreference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.util.EnumSet;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification that the Kernel notification bridge speaks the
 * exact wire protocol documented by the Kernel's OpenAPI spec
 * ({@code /api/notifications/*}), using a stubbed HTTP server instead of the
 * real {@code kernel-core.yowyob.com/kernel-api}.
 *
 * <p>Also covers the {@code X-Tenant-Id}/{@code X-Organization-Id} headers —
 * undocumented in the OpenAPI spec but required by the Kernel's gateway
 * (confirmed against the real server: missing either fails closed with
 * {@code TENANT_CONTEXT_REQUIRED} / {@code ORGANIZATION_CONTEXT_REQUIRED}).
 *
 * @author MANFOUO Braun
 */
class KernelNotificationBridgeTest {

    private static final String TENANT_ID = "5b1f6e2a-9999-4c3a-9a2a-0000000000aa";
    private static final String ORGANIZATION_ID = "5b1f6e2a-9999-4c3a-9a2a-0000000000bb";

    private static WireMockServer wireMock;
    private static KernelNotificationClient client;

    @BeforeAll
    static void startServer() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        WebClient webClient = WebClient.builder().baseUrl(wireMock.baseUrl()).build();
        client = new KernelNotificationClient(webClient);
    }

    @AfterAll
    static void stopServer() {
        wireMock.stop();
    }

    @AfterEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Test
    void sendMessage_shouldPostToDeliveriesEndpoint_withMappedChannelAndTenantHeaders() {
        wireMock.stubFor(post(urlEqualTo("/api/notifications/deliveries"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "success": true,
                                  "data": {
                                    "id": "5b1f6e2a-1111-4c3a-9a2a-000000000001",
                                    "channel": "EMAIL",
                                    "status": "SENT",
                                    "recipientAddress": "user@example.com",
                                    "body": "hello"
                                  }
                                }
                                """)));

        KernelDeliveryProviderAdapter adapter = new KernelDeliveryProviderAdapter(client);

        StepVerifier.create(adapter.sendMessage(NotificationChannel.EMAIL, TENANT_ID, ORGANIZATION_ID,
                        "user@example.com", "hello"))
                .verifyComplete();

        wireMock.verify(postRequestedFor(urlEqualTo("/api/notifications/deliveries"))
                .withHeader("X-Tenant-Id", equalTo(TENANT_ID))
                .withHeader("X-Organization-Id", equalTo(ORGANIZATION_ID))
                .withRequestBody(matchingJsonPath("$.channel", equalTo("EMAIL")))
                .withRequestBody(matchingJsonPath("$.recipientAddress", equalTo("user@example.com")))
                .withRequestBody(matchingJsonPath("$.body", equalTo("hello"))));
    }

    @Test
    void sendMessage_shouldMapEachTiiBnTickChannel_toItsKernelEquivalent() {
        wireMock.stubFor(post(urlEqualTo("/api/notifications/deliveries"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": {"id": "5b1f6e2a-1111-4c3a-9a2a-000000000002", "status": "SENT"}}
                                """)));
        KernelDeliveryProviderAdapter adapter = new KernelDeliveryProviderAdapter(client);

        StepVerifier.create(adapter.sendMessage(NotificationChannel.SMS_LOCAL, TENANT_ID, ORGANIZATION_ID,
                        "+237691000001", "hi"))
                .verifyComplete();
        StepVerifier.create(adapter.sendMessage(NotificationChannel.WHATSAPP, TENANT_ID, ORGANIZATION_ID,
                        "+237691000001", "hi"))
                .verifyComplete();
        StepVerifier.create(adapter.sendMessage(NotificationChannel.PUSH_FCM, TENANT_ID, ORGANIZATION_ID,
                        "fcm-token", "hi"))
                .verifyComplete();

        wireMock.verify(postRequestedFor(urlEqualTo("/api/notifications/deliveries"))
                .withRequestBody(matchingJsonPath("$.channel", equalTo("SMS"))));
        wireMock.verify(postRequestedFor(urlEqualTo("/api/notifications/deliveries"))
                .withRequestBody(matchingJsonPath("$.channel", equalTo("WHATSAPP"))));
        wireMock.verify(postRequestedFor(urlEqualTo("/api/notifications/deliveries"))
                .withRequestBody(matchingJsonPath("$.channel", equalTo("PUSH"))));
    }

    @Test
    void findByUserId_shouldMergePerChannelKernelRows_intoOneAggregate() {
        String userId = "5b1f6e2a-2222-4c3a-9a2a-000000000003";
        wireMock.stubFor(get(urlPathEqualTo("/api/notifications/preferences/users/" + userId))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": [
                                  {"userId": "%s", "channel": "EMAIL", "enabled": true,  "locale": "fr_CM"},
                                  {"userId": "%s", "channel": "SMS",   "enabled": false, "locale": "fr_CM"},
                                  {"userId": "%s", "channel": "WHATSAPP", "enabled": true, "locale": "en_CM"}
                                ]}
                                """.formatted(userId, userId, userId))));

        KernelNotificationPreferenceAdapter adapter = new KernelNotificationPreferenceAdapter(client);

        StepVerifier.create(adapter.findByUserId(TENANT_ID, ORGANIZATION_ID, userId))
                .assertNext(pref -> {
                    assertThat(pref.getUserId()).isEqualTo(userId);
                    assertThat(pref.areNotificationsEnabled()).isTrue();
                    assertThat(pref.getActiveChannels())
                            .containsExactlyInAnyOrder(NotificationChannel.EMAIL, NotificationChannel.WHATSAPP);
                    assertThat(pref.acceptsChannel(NotificationChannel.SMS_LOCAL)).isFalse();
                })
                .verifyComplete();

        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/notifications/preferences/users/" + userId))
                .withHeader("X-Tenant-Id", equalTo(TENANT_ID))
                .withHeader("X-Organization-Id", equalTo(ORGANIZATION_ID)));
    }

    @Test
    void findByUserId_shouldReturnEmpty_whenKernelHasNoRowsYet() {
        String userId = "5b1f6e2a-3333-4c3a-9a2a-000000000004";
        wireMock.stubFor(get(urlPathEqualTo("/api/notifications/preferences/users/" + userId))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": []}
                                """)));

        KernelNotificationPreferenceAdapter adapter = new KernelNotificationPreferenceAdapter(client);

        StepVerifier.create(adapter.findByUserId(TENANT_ID, ORGANIZATION_ID, userId)).verifyComplete();
    }

    @Test
    void save_shouldUpsertOneKernelRowPerChannel_thenReturnMergedAggregate() {
        String userId = "5b1f6e2a-4444-4c3a-9a2a-000000000005";
        wireMock.stubFor(post(urlEqualTo("/api/notifications/preferences"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": {"userId": "%s", "channel": "EMAIL", "enabled": true, "locale": "fr_CM"}}
                                """.formatted(userId))));
        wireMock.stubFor(get(urlPathEqualTo("/api/notifications/preferences/users/" + userId))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": [{"userId": "%s", "channel": "EMAIL", "enabled": true, "locale": "fr_CM"}]}
                                """.formatted(userId))));

        KernelNotificationPreferenceAdapter adapter = new KernelNotificationPreferenceAdapter(client);
        Set<NotificationChannel> onlyEmail = EnumSet.of(NotificationChannel.EMAIL);
        NotificationPreference toSave = new NotificationPreference(userId, TENANT_ID, ORGANIZATION_ID, onlyEmail,
                "fr_CM", true);

        StepVerifier.create(adapter.save(toSave))
                .assertNext(pref -> assertThat(pref.getActiveChannels()).containsExactly(NotificationChannel.EMAIL))
                .verifyComplete();

        // One upsert per NotificationChannel value (5), each carrying the right enabled flag
        wireMock.verify(5, postRequestedFor(urlEqualTo("/api/notifications/preferences"))
                .withHeader("X-Tenant-Id", equalTo(TENANT_ID))
                .withHeader("X-Organization-Id", equalTo(ORGANIZATION_ID)));
        wireMock.verify(postRequestedFor(urlEqualTo("/api/notifications/preferences"))
                .withRequestBody(matchingJsonPath("$.channel", equalTo("EMAIL")))
                .withRequestBody(matchingJsonPath("$.enabled", equalTo("true"))));
        wireMock.verify(postRequestedFor(urlEqualTo("/api/notifications/preferences"))
                .withRequestBody(matchingJsonPath("$.channel", equalTo("SMS")))
                .withRequestBody(matchingJsonPath("$.enabled", equalTo("false"))));
    }
}
