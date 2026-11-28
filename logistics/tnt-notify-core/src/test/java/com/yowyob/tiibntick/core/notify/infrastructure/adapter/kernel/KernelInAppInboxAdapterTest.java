package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.yowyob.tiibntick.core.notify.application.service.InAppInboxService;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import com.yowyob.tiibntick.core.notify.domain.model.InAppMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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
 * Decision D-157: the in-app inbox is the Kernel's channel {@code IN_APP}. Wire protocol checked on a stubbed Kernel
 * (OpenAPI {@code POST /api/notifications/deliveries}, {@code GET /api/notifications/inbox},
 * {@code POST /api/notifications/inbox/{id}/read}): the message is written with the service account, the inbox is
 * read and marked with the user's own token, always under the notification organization and its tenant.
 */
class KernelInAppInboxAdapterTest {

    private static final String TENANT = "fbd90da6-629d-4d78-9a36-8735ff1762cb";
    private static final String ORG = "0f0f0f0f-0000-4000-8000-0000000000aa";

    private static WireMockServer wireMock;
    private KernelInAppInboxAdapter adapter;
    private InAppInboxService service;

    @BeforeAll
    static void start() {
        wireMock = new WireMockServer(0);
        wireMock.start();
    }

    @AfterAll
    static void stop() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        NotifyProperties properties = new NotifyProperties();
        properties.getKernel().setDefaultTenantId(TENANT);
        properties.getKernel().setDefaultOrganizationId(ORG);
        KernelNotificationClient client = new KernelNotificationClient(
                WebClient.builder().baseUrl(wireMock.baseUrl()).defaultHeader("Authorization", "Bearer api-key-only")
                        .build(), properties);
        client.setKernelServiceAccountWebClient(WebClient.builder().baseUrl(wireMock.baseUrl())
                .defaultHeader("Authorization", "Bearer service-account").build());
        client.setKernelUserWebClient(WebClient.builder().baseUrl(wireMock.baseUrl())
                .defaultHeader("Authorization", "Bearer the-user").build());
        adapter = new KernelInAppInboxAdapter(client);
        service = new InAppInboxService(adapter);
    }

    @Test
    void sendWritesAnInAppDeliveryWithTheServiceAccount() {
        UUID recipient = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        wireMock.stubFor(post(urlEqualTo("/api/notifications/deliveries")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + id + "\",\"recipientUserId\":\"" + recipient + "\",\"channel\":\"IN_APP\","
                        + "\"subject\":\"Colis récupéré\",\"body\":\"Ton colis est en route\",\"status\":\"SENT\","
                        + "\"metadata\":{\"type\":\"PARCEL_PICKED_UP\"},\"requestedAt\":\"2026-11-27T10:00:00Z\"}")));

        StepVerifier.create(service.send(recipient, "tiibntick-go", "Colis récupéré", "Ton colis est en route",
                        Map.of("type", "PARCEL_PICKED_UP", "linkId", "x")))
                .assertNext(m -> {
                    assertThat(m.id()).isEqualTo(id);
                    assertThat(m.meta("type")).isEqualTo("PARCEL_PICKED_UP");
                    assertThat(m.createdAt()).isEqualTo(Instant.parse("2026-11-27T10:00:00Z"));
                })
                .verifyComplete();

        wireMock.verify(postRequestedFor(urlEqualTo("/api/notifications/deliveries"))
                .withHeader("Authorization", equalTo("Bearer service-account"))
                .withHeader("X-Tenant-Id", equalTo(TENANT))
                .withHeader("X-Organization-Id", equalTo(ORG))
                .withRequestBody(matchingJsonPath("$.channel", equalTo("IN_APP")))
                .withRequestBody(matchingJsonPath("$.recipientUserId", equalTo(recipient.toString())))
                .withRequestBody(matchingJsonPath("$.subject", equalTo("Colis récupéré")))
                .withRequestBody(matchingJsonPath("$.body", equalTo("Ton colis est en route")))
                .withRequestBody(matchingJsonPath("$.application", equalTo("tiibntick-go")))
                .withRequestBody(matchingJsonPath("$.metadata.type", equalTo("PARCEL_PICKED_UP"))));
    }

    @Test
    void inboxIsReadWithTheUsersTokenAndKeepsOnlyInAppMessages() {
        UUID unread = UUID.randomUUID();
        UUID read = UUID.randomUUID();
        wireMock.stubFor(get(urlPathEqualTo("/api/notifications/inbox")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("[{\"id\":\"" + unread + "\",\"channel\":\"IN_APP\",\"subject\":\"a\",\"body\":\"b\","
                        + "\"metadata\":{\"type\":\"CANDIDATE_APPLIED\",\"linkKind\":\"SHIPMENT\"},"
                        + "\"requestedAt\":\"2026-11-27T10:00:00Z\",\"attempts\":1},"
                        + "{\"id\":\"" + read + "\",\"channel\":\"IN_APP\",\"subject\":\"c\",\"body\":\"d\","
                        + "\"readAt\":\"2026-11-27T11:00:00Z\"},"
                        + "{\"id\":\"" + UUID.randomUUID() + "\",\"channel\":\"EMAIL\",\"subject\":\"e\",\"body\":\"f\"}]")));

        StepVerifier.create(service.mine("tiibntick-go").collectList())
                .assertNext(list -> {
                    assertThat(list).extracting(InAppMessage::id).containsExactly(unread, read);
                    assertThat(list.get(0).readAt()).isNull();
                    assertThat(list.get(0).meta("linkKind")).isEqualTo("SHIPMENT");
                    assertThat(list.get(1).readAt()).isEqualTo(Instant.parse("2026-11-27T11:00:00Z"));
                })
                .verifyComplete();

        wireMock.verify(getRequestedFor(urlEqualTo("/api/notifications/inbox?application=tiibntick-go"))
                .withHeader("Authorization", equalTo("Bearer the-user"))
                .withHeader("X-Tenant-Id", equalTo(TENANT))
                .withHeader("X-Organization-Id", equalTo(ORG)));
    }

    @Test
    void markReadUsesTheUsersToken() {
        UUID id = UUID.randomUUID();
        wireMock.stubFor(post(urlEqualTo("/api/notifications/inbox/" + id + "/read")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + id + "\",\"channel\":\"IN_APP\",\"subject\":\"a\",\"body\":\"b\","
                        + "\"readAt\":\"2026-11-27T12:00:00Z\"}")));

        StepVerifier.create(service.markRead(id))
                .assertNext(m -> assertThat(m.readAt()).isEqualTo(Instant.parse("2026-11-27T12:00:00Z")))
                .verifyComplete();
        wireMock.verify(postRequestedFor(urlEqualTo("/api/notifications/inbox/" + id + "/read"))
                .withHeader("Authorization", equalTo("Bearer the-user")));
    }

    @Test
    void kernelErrorsPropagateInsteadOfAnEmptyInbox() {
        wireMock.stubFor(get(urlPathEqualTo("/api/notifications/inbox")).willReturn(aResponse().withStatus(503)));
        StepVerifier.create(service.mine("tiibntick-go")).expectError().verify();
    }

    @Test
    void withoutTheUserClientTheInboxFailsExplicitly() {
        NotifyProperties properties = new NotifyProperties();
        properties.getKernel().setDefaultTenantId(TENANT);
        properties.getKernel().setDefaultOrganizationId(ORG);
        KernelNotificationClient bare = new KernelNotificationClient(
                WebClient.builder().baseUrl(wireMock.baseUrl()).build(), properties);
        StepVerifier.create(new KernelInAppInboxAdapter(bare).mine(null))
                .expectError(KernelContextMissingException.class).verify();
    }

    @Test
    void aMessageNeedsARecipientAndABody() {
        StepVerifier.create(service.send(null, "tiibntick-go", "t", "b", Map.of()))
                .expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(service.send(UUID.randomUUID(), "tiibntick-go", "t", " ", Map.of()))
                .expectError(IllegalArgumentException.class).verify();
    }
}
