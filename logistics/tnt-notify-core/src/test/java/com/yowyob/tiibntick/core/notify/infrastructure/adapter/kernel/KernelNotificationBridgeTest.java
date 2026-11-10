package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationPreference;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort;
import com.yowyob.tiibntick.core.notify.application.port.out.ITranslationPort;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import com.yowyob.tiibntick.core.notify.domain.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.SendNotificationRequestDto;
import reactor.core.publisher.Mono;
import java.util.Map;
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
        client = clientWith(null, null);
    }

    private static KernelNotificationClient clientWith(String defaultTenant, String defaultOrganization) {
        NotifyProperties properties = new NotifyProperties();
        properties.getKernel().setDefaultTenantId(defaultTenant);
        properties.getKernel().setDefaultOrganizationId(defaultOrganization);
        return new KernelNotificationClient(WebClient.builder().baseUrl(wireMock.baseUrl()).build(), properties);
    }

    private KernelDeliveryAdapter adapter(boolean templatesEnabled) {
        return new KernelDeliveryAdapter(client, new KernelTemplateRegistrar(client, new FakeTranslation()),
                templatesEnabled);
    }

    private static INotificationDeliveryPort.OutboundMessage message(NotificationChannel channel, String recipientId,
            String destination) {
        return new INotificationDeliveryPort.OutboundMessage(channel, TENANT_ID, ORGANIZATION_ID, recipientId,
                destination, "notify.gofp.account_approved", "fr_CM", Map.of("loginUrl", "https://x"), "Bienvenue",
                NotificationPriority.HIGH);
    }

    private static final String DELIVERY_OK = """
            {"success": true, "data": {"id": "5b1f6e2a-1111-4c3a-9a2a-000000000001", "status": "SENT"}}
            """;

    /** Minimal i18n stand-in: every key has the same raw pattern. */
    private static final class FakeTranslation implements ITranslationPort {
        @Override
        public Mono<String> translate(NotificationModel model) {
            return Mono.just("texte");
        }

        @Override
        public Mono<String> rawTemplate(String templateKey, String language) {
            return Mono.just("Bienvenue {{loginUrl}}");
        }
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
    void deliver_shouldPostToDeliveriesEndpoint_withTenantHeadersTemplateAndVariables() {
        wireMock.stubFor(get(urlPathEqualTo("/api/notifications/templates"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\": true, \"data\": []}")));
        wireMock.stubFor(post(urlEqualTo("/api/notifications/templates"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\": true, \"data\": {\"code\": \"notify.gofp.account_approved\"}}")));
        wireMock.stubFor(post(urlEqualTo("/api/notifications/deliveries"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(DELIVERY_OK)));

        StepVerifier.create(adapter(true).deliver(message(NotificationChannel.EMAIL, "someone@x.com", "someone@x.com")))
                .assertNext(r -> {
                    assertThat(r.deliveryId()).isEqualTo("5b1f6e2a-1111-4c3a-9a2a-000000000001");
                    assertThat(r.status()).isEqualTo("SENT");
                })
                .verifyComplete();

        wireMock.verify(postRequestedFor(urlEqualTo("/api/notifications/deliveries"))
                .withHeader("X-Tenant-Id", equalTo(TENANT_ID))
                .withHeader("X-Organization-Id", equalTo(ORGANIZATION_ID))
                .withRequestBody(matchingJsonPath("$.channel", equalTo("EMAIL")))
                .withRequestBody(matchingJsonPath("$.recipientAddress", equalTo("someone@x.com")))
                .withRequestBody(matchingJsonPath("$.templateCode", equalTo("notify.gofp.account_approved")))
                .withRequestBody(matchingJsonPath("$.variables.loginUrl", equalTo("https://x")))
                .withRequestBody(matchingJsonPath("$.metadata.priority", equalTo("HIGH")))
                .withRequestBody(matchingJsonPath("$.body", equalTo("Bienvenue"))));
    }

    @Test
    void deliver_shouldRegisterTheTemplateOnceThenReuseIt() {
        wireMock.stubFor(get(urlPathEqualTo("/api/notifications/templates"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\": true, \"data\": []}")));
        wireMock.stubFor(post(urlEqualTo("/api/notifications/templates"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\": true, \"data\": {\"code\": \"c\"}}")));
        wireMock.stubFor(post(urlEqualTo("/api/notifications/deliveries"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(DELIVERY_OK)));
        KernelDeliveryAdapter adapter = adapter(true);

        StepVerifier.create(adapter.deliver(message(NotificationChannel.SMS_LOCAL, null, "+237691000001")))
                .expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.deliver(message(NotificationChannel.SMS_LOCAL, null, "+237691000001")))
                .expectNextCount(1).verifyComplete();

        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/notifications/templates"))
                .withRequestBody(matchingJsonPath("$.code", equalTo("notify.gofp.account_approved")))
                .withRequestBody(matchingJsonPath("$.channel", equalTo("SMS")))
                .withRequestBody(matchingJsonPath("$.locale", equalTo("fr_CM")))
                .withRequestBody(matchingJsonPath("$.bodyTemplate", equalTo("Bienvenue {{loginUrl}}"))));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/notifications/templates")));
        wireMock.verify(2, postRequestedFor(urlEqualTo("/api/notifications/deliveries")));
    }

    @Test
    void deliver_shouldStillSend_whenTemplateRegistrationFails() {
        wireMock.stubFor(get(urlPathEqualTo("/api/notifications/templates"))
                .willReturn(aResponse().withStatus(500)));
        wireMock.stubFor(post(urlEqualTo("/api/notifications/deliveries"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(DELIVERY_OK)));

        StepVerifier.create(adapter(true).deliver(message(NotificationChannel.WHATSAPP, null, "+237691000001")))
                .expectNextCount(1).verifyComplete();
        wireMock.verify(postRequestedFor(urlEqualTo("/api/notifications/deliveries"))
                .withRequestBody(matchingJsonPath("$.body", equalTo("Bienvenue"))));
    }

    @Test
    void deliver_withTemplatesDisabled_shouldSendOnlyTheBody() {
        wireMock.stubFor(post(urlEqualTo("/api/notifications/deliveries"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(DELIVERY_OK)));

        StepVerifier.create(adapter(false).deliver(message(NotificationChannel.EMAIL, null, "a@b.c")))
                .expectNextCount(1).verifyComplete();

        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/api/notifications/templates")));
        wireMock.verify(postRequestedFor(urlEqualTo("/api/notifications/deliveries"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.notMatching(".*\"templateCode\":\"[^\"]+\".*")));
    }

    @Test
    void deliver_shouldAddressInAppAndPushToTheUser_notToAnAddress() {
        wireMock.stubFor(post(urlEqualTo("/api/notifications/deliveries"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(DELIVERY_OK)));
        String user = "5b1f6e2a-7777-4c3a-9a2a-0000000000cc";
        KernelDeliveryAdapter adapter = adapter(false);

        StepVerifier.create(adapter.deliver(message(NotificationChannel.IN_APP_WEBSOCKET, user, user)))
                .expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.deliver(message(NotificationChannel.PUSH_FCM, user, user)))
                .expectNextCount(1).verifyComplete();

        wireMock.verify(postRequestedFor(urlEqualTo("/api/notifications/deliveries"))
                .withRequestBody(matchingJsonPath("$.channel", equalTo("WEBSOCKET")))
                .withRequestBody(matchingJsonPath("$.recipientUserId", equalTo(user)))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.notMatching(".*\"recipientAddress\":\"[^\"]+\".*")));
        wireMock.verify(postRequestedFor(urlEqualTo("/api/notifications/deliveries"))
                .withRequestBody(matchingJsonPath("$.channel", equalTo("PUSH")))
                .withRequestBody(matchingJsonPath("$.recipientUserId", equalTo(user))));
    }

    @Test
    void deliver_shouldKeepTheAddress_andAddTheUserId_forMailToAKnownUser() {
        wireMock.stubFor(post(urlEqualTo("/api/notifications/deliveries"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(DELIVERY_OK)));
        String user = "5b1f6e2a-7777-4c3a-9a2a-0000000000dd";

        StepVerifier.create(adapter(false).deliver(message(NotificationChannel.EMAIL, user, "u@x.com")))
                .expectNextCount(1).verifyComplete();

        wireMock.verify(postRequestedFor(urlEqualTo("/api/notifications/deliveries"))
                .withRequestBody(matchingJsonPath("$.recipientUserId", equalTo(user)))
                .withRequestBody(matchingJsonPath("$.recipientAddress", equalTo("u@x.com"))));
    }

    @Test
    void client_shouldFallBackToTheConfiguredTenantAndOrganization_whenTheCallerHasNone() {
        wireMock.stubFor(post(urlEqualTo("/api/notifications/deliveries"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(DELIVERY_OK)));
        KernelNotificationClient withDefaults = clientWith("5b1f6e2a-aaaa-4c3a-9a2a-000000000001",
                "5b1f6e2a-aaaa-4c3a-9a2a-000000000002");

        StepVerifier.create(withDefaults.send(null, null, new SendNotificationRequestDto(
                        null, "a@b.c", "EMAIL", null, null, "x", null, null)))
                .expectNextCount(1).verifyComplete();

        wireMock.verify(postRequestedFor(urlEqualTo("/api/notifications/deliveries"))
                .withHeader("X-Tenant-Id", equalTo("5b1f6e2a-aaaa-4c3a-9a2a-000000000001"))
                .withHeader("X-Organization-Id", equalTo("5b1f6e2a-aaaa-4c3a-9a2a-000000000002")));
    }

    @Test
    void client_shouldFailExplicitly_withoutCallingTheKernel_whenNoOrganizationIsAvailable() {
        StepVerifier.create(client.send(TENANT_ID, null, new SendNotificationRequestDto(
                        null, "a@b.c", "EMAIL", null, null, "x", null, null)))
                .expectErrorSatisfies(e -> assertThat(e).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("organization"))
                .verify();
        wireMock.verify(0, postRequestedFor(urlEqualTo("/api/notifications/deliveries")));
    }

    @Test
    void search_shouldFilterTheKernelDeliveryLog_byRecipientStatusAndId() {
        String user = "5b1f6e2a-7777-4c3a-9a2a-0000000000ee";
        wireMock.stubFor(get(urlPathEqualTo("/api/notifications/deliveries"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": [
                                  {"id": "5b1f6e2a-0001-4c3a-9a2a-000000000001", "recipientUserId": "%s",
                                   "channel": "PUSH", "status": "SENT", "body": "a", "metadata": {"priority": "HIGH"}},
                                  {"id": "5b1f6e2a-0001-4c3a-9a2a-000000000002", "recipientUserId": "%s",
                                   "channel": "WEBSOCKET", "status": "QUEUED", "body": "b"},
                                  {"id": "5b1f6e2a-0001-4c3a-9a2a-000000000003",
                                   "recipientUserId": "5b1f6e2a-0000-4c3a-9a2a-0000000000ff",
                                   "channel": "EMAIL", "status": "FAILED", "body": "c"}
                                ]}
                                """.formatted(user, user))));
        KernelNotificationSearchAdapter search = new KernelNotificationSearchAdapter(client);

        StepVerifier.create(search.findByRecipientId(TENANT_ID, ORGANIZATION_ID, user).collectList())
                .assertNext(list -> assertThat(list).hasSize(2)).verifyComplete();
        StepVerifier.create(search.findPendingByRecipient(TENANT_ID, ORGANIZATION_ID, user))
                .assertNext(n -> {
                    assertThat(n.getStatus()).isEqualTo(DeliveryStatus.PENDING);
                    assertThat(n.getChannel()).isEqualTo(NotificationChannel.IN_APP_WEBSOCKET);
                }).verifyComplete();
        StepVerifier.create(search.findByStatus(TENANT_ID, ORGANIZATION_ID, DeliveryStatus.FAILED))
                .assertNext(n -> assertThat(n.getChannel()).isEqualTo(NotificationChannel.EMAIL)).verifyComplete();
        StepVerifier.create(search.findById(TENANT_ID, ORGANIZATION_ID, "5b1f6e2a-0001-4c3a-9a2a-000000000001"))
                .assertNext(n -> assertThat(n.getPriority()).isEqualTo(NotificationPriority.HIGH)).verifyComplete();
        StepVerifier.create(search.findById(TENANT_ID, ORGANIZATION_ID, "unknown")).verifyComplete();
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
