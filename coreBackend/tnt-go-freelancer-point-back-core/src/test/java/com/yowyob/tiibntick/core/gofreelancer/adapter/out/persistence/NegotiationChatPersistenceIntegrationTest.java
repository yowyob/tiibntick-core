package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.GofpUserR2dbcRepository;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.NegotiationMessageRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.application.service.NegotiationChatHub;
import com.yowyob.tiibntick.core.gofreelancer.application.service.NegotiationChatService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The negotiation chat against a real Postgres created from the module's own changelog SQL: the
 * service, the Spring Data repository (insert of an assigned id, TIMESTAMP ↔ Instant, the read/unread
 * queries) and the live hub work together exactly as in the application. Delivery lookups, user
 * names and push are the only doubles.
 */
@SpringBootTest(classes = NegotiationChatPersistenceIntegrationTest.TestConfig.class)
@Testcontainers
@Tag("integration")
class NegotiationChatPersistenceIntegrationTest {

    static final UUID TENANT = UUID.randomUUID();
    static final UUID ANNOUNCEMENT = UUID.randomUUID();
    static final UUID CLIENT = UUID.randomUUID();
    static final UUID PERSON_A = UUID.randomUUID();
    static final UUID ACTOR_A = UUID.randomUUID();
    static final UUID PERSON_B = UUID.randomUUID();
    static final UUID ACTOR_B = UUID.randomUUID();
    static final UUID STRANGER = UUID.randomUUID();

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_gofp_chat_test").withUsername("tnt_test").withPassword("tnt_test_secret");

    @BeforeAll
    static void createSchema() throws Exception {
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = c.createStatement()) {
            st.execute(Files.readString(Path.of("src/main/resources/db/changelog/20261112_create_negotiation_messages.sql")));
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + POSTGRES.getDatabaseName());
        r.add("spring.r2dbc.username", POSTGRES::getUsername);
        r.add("spring.r2dbc.password", POSTGRES::getPassword);
    }

    @Autowired NegotiationChatService chat;
    @Autowired NegotiationChatHub hub;
    @Autowired NegotiationMessageRepository repository;

    @Test
    @DisplayName("messages are stored per conversation, oldest first, with the right roles")
    void storesAndListsPerConversation() {
        StepVerifier.create(chat.post(ANNOUNCEMENT, CLIENT, PERSON_A, "Pouvez-vous descendre à 2000 ?")).expectNextCount(1).verifyComplete();
        StepVerifier.create(chat.post(ANNOUNCEMENT, ACTOR_A, null, "Je peux à 2200")).expectNextCount(1).verifyComplete();
        StepVerifier.create(chat.post(ANNOUNCEMENT, ACTOR_B, null, "2500 pour moi")).expectNextCount(1).verifyComplete();

        StepVerifier.create(chat.list(ANNOUNCEMENT, CLIENT, PERSON_A).collectList())
                .assertNext(l -> {
                    assertThat(l).extracting(NegotiationChatService.ChatMessage::content)
                            .containsExactly("Pouvez-vous descendre à 2000 ?", "Je peux à 2200");
                    assertThat(l).extracting(NegotiationChatService.ChatMessage::senderRole).containsExactly("CLIENT", "FREELANCER");
                    assertThat(l.get(0).createdAt()).isNotNull();
                }).verifyComplete();
        // B only ever sees their own thread, whichever id they put in the request
        StepVerifier.create(chat.list(ANNOUNCEMENT, ACTOR_B, PERSON_A).map(NegotiationChatService.ChatMessage::content).collectList())
                .assertNext(l -> assertThat(l).containsExactly("2500 pour moi")).verifyComplete();
        StepVerifier.create(chat.list(ANNOUNCEMENT, STRANGER, PERSON_A)).expectError(AccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("unread counts and read receipts work on the real tables")
    void unreadAndReceipts() {
        UUID ann = UUID.randomUUID();
        stubAnnouncement(ann);
        chat.post(ann, CLIENT, PERSON_A, "un").block();
        chat.post(ann, CLIENT, PERSON_A, "deux").block();
        chat.post(ann, ACTOR_A, null, "reponse").block();

        // the delivery person has 2 unread (the client's), the client has 1 (the driver's)
        StepVerifier.create(chat.threads(ann, ACTOR_A)).assertNext(t -> assertThat(t.unread()).isEqualTo(2)).verifyComplete();
        StepVerifier.create(chat.threads(ann, CLIENT).collectList()).assertNext(l -> {
            assertThat(l.get(0).unread()).isEqualTo(1);
            assertThat(l.get(0).lastMessage().content()).isEqualTo("reponse");
        }).verifyComplete();

        List<String> live = new ArrayList<>();
        hub.subscribe(ann, ACTOR_A).subscribe(e -> live.add(e.type()));
        StepVerifier.create(chat.markRead(ann, ACTOR_A, null)).expectNext(2L).verifyComplete();
        StepVerifier.create(chat.markRead(ann, ACTOR_A, null)).expectNext(0L).verifyComplete();
        assertThat(live).containsExactly("read");

        StepVerifier.create(chat.threads(ann, ACTOR_A)).assertNext(t -> assertThat(t.unread()).isZero()).verifyComplete();
        // the driver's own message is still unread for the client: reading never marks your own messages
        StepVerifier.create(chat.threads(ann, CLIENT).collectList()).assertNext(l -> assertThat(l.get(0).unread()).isEqualTo(1)).verifyComplete();
        StepVerifier.create(chat.list(ann, CLIENT, PERSON_A).collectList()).assertNext(l -> {
            assertThat(l.get(0).readAt()).isNotNull();   // client's messages were read by the driver
            assertThat(l.get(2).readAt()).isNull();      // driver's reply not read by the client yet
        }).verifyComplete();
    }

    @Test
    @DisplayName("a stored message reaches a subscriber of the live stream")
    void liveDelivery() {
        UUID ann = UUID.randomUUID();
        stubAnnouncement(ann);
        StepVerifier.create(chat.stream(ann, CLIENT, PERSON_A).take(1))
                .then(() -> chat.post(ann, CLIENT, PERSON_A, "en direct").block())
                .assertNext(e -> {
                    assertThat(e.type()).isEqualTo("message");
                    assertThat(e.message().content()).isEqualTo("en direct");
                }).verifyComplete();
        assertThat(repository.findByAnnouncementIdAndThreadUserIdOrderByCreatedAtAsc(ann, ACTOR_A).collectList().block()).hasSize(1);
    }

    @Test
    @DisplayName("the database refuses a blank message or an unknown role")
    void constraints() {
        var blank = com.yowyob.tiibntick.core.gofreelancer.domain.model.NegotiationMessage.builder()
                .id(UUID.randomUUID()).announcementId(ANNOUNCEMENT).threadUserId(ACTOR_A).senderUserId(CLIENT)
                .senderRole("CLIENT").content("   ").createdAt(java.time.Instant.now()).build();
        StepVerifier.create(repository.save(blank)).expectError().verify();
        var badRole = com.yowyob.tiibntick.core.gofreelancer.domain.model.NegotiationMessage.builder()
                .id(UUID.randomUUID()).announcementId(ANNOUNCEMENT).threadUserId(ACTOR_A).senderUserId(CLIENT)
                .senderRole("ADMIN").content("x").createdAt(java.time.Instant.now()).build();
        StepVerifier.create(repository.save(badRole)).expectError().verify();
    }

    @Autowired DeliveryQueryUseCase deliveryQuery;

    private void stubAnnouncement(UUID announcementId) {
        DeliveryAnnouncement announcement = announcement();
        when(deliveryQuery.findAnnouncementById(any(), org.mockito.ArgumentMatchers.eq(announcementId)))
                .thenReturn(Mono.just(announcement));
    }

    static DeliveryAnnouncement announcement() {
        AnnouncementResponse a = mock(AnnouncementResponse.class);
        when(a.getDeliveryPersonId()).thenReturn(PERSON_A);
        AnnouncementResponse b = mock(AnnouncementResponse.class);
        when(b.getDeliveryPersonId()).thenReturn(PERSON_B);
        DeliveryAnnouncement announcement = mock(DeliveryAnnouncement.class);
        when(announcement.getClientId()).thenReturn(CLIENT);
        when(announcement.getResponses()).thenReturn(List.of(a, b));
        return announcement;
    }

    @Configuration
    @Import({R2dbcAutoConfiguration.class, R2dbcTransactionManagerAutoConfiguration.class,
            DataR2dbcAutoConfiguration.class})
    @EnableR2dbcRepositories(basePackageClasses = NegotiationMessageRepository.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = NegotiationMessageRepository.class))
    static class TestConfig {

        @Bean DeliveryQueryUseCase deliveryQuery() {
            DeliveryAnnouncement defaultAnnouncement = announcement(); // built before stubbing: mocks cannot nest in when()
            DeliveryQueryUseCase q = mock(DeliveryQueryUseCase.class);
            when(q.findAnnouncementById(any(), org.mockito.ArgumentMatchers.eq(ANNOUNCEMENT)))
                    .thenReturn(Mono.just(defaultAnnouncement));
            when(q.resolveActorIdForDeliveryPerson(any(), org.mockito.ArgumentMatchers.eq(PERSON_A))).thenReturn(Mono.just(ACTOR_A));
            when(q.resolveActorIdForDeliveryPerson(any(), org.mockito.ArgumentMatchers.eq(PERSON_B))).thenReturn(Mono.just(ACTOR_B));
            return q;
        }

        @Bean GofpUserR2dbcRepository users() {
            GofpUserR2dbcRepository u = mock(GofpUserR2dbcRepository.class);
            when(u.findByCoreUserId(any())).thenReturn(Mono.empty());
            return u;
        }

        @Bean TenantContextHolder tenantContextHolder() {
            TenantContextHolder t = mock(TenantContextHolder.class);
            when(t.currentTenantId()).thenReturn(Mono.just(TENANT));
            return t;
        }

        @Bean PushNotificationPort push() {
            PushNotificationPort p = mock(PushNotificationPort.class);
            when(p.sendPushNotification(any(), any(), any(), any())).thenReturn(Mono.empty());
            return p;
        }

        @Bean NegotiationChatHub hub() {
            return new NegotiationChatHub();
        }

        @Bean NegotiationChatService chat(DeliveryQueryUseCase q, NegotiationMessageRepository repo,
                                          GofpUserR2dbcRepository users, TenantContextHolder tenant,
                                          NegotiationChatHub hub, PushNotificationPort push) {
            return new NegotiationChatService(q, repo, users, tenant, hub, push);
        }
    }
}
