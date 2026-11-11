package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.GofpUserR2dbcRepository;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.NegotiationMessageRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.application.service.NegotiationChatHub.ChatEvent;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.NegotiationMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Negotiation: one conversation per (announcement, delivery person)")
class NegotiationChatServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID announcementId = UUID.randomUUID();
    private final UUID client = UUID.randomUUID();
    private final UUID personA = UUID.randomUUID();
    private final UUID actorA = UUID.randomUUID();
    private final UUID personB = UUID.randomUUID();
    private final UUID actorB = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    private final DeliveryQueryUseCase query = mock(DeliveryQueryUseCase.class);
    private final NegotiationMessageRepository messages = mock(NegotiationMessageRepository.class);
    private final GofpUserR2dbcRepository users = mock(GofpUserR2dbcRepository.class);
    private final TenantContextHolder tenantHolder = mock(TenantContextHolder.class);
    private final NegotiationChatHub hub = new NegotiationChatHub();
    private final PushNotificationPort push = mock(PushNotificationPort.class);
    private final NegotiationChatService service = new NegotiationChatService(query, messages, users, tenantHolder, hub, push);

    NegotiationChatServiceTest() {
        when(tenantHolder.currentTenantId()).thenReturn(Mono.just(tenant));
        AnnouncementResponse a = mock(AnnouncementResponse.class);
        when(a.getDeliveryPersonId()).thenReturn(personA);
        AnnouncementResponse b = mock(AnnouncementResponse.class);
        when(b.getDeliveryPersonId()).thenReturn(personB);
        DeliveryAnnouncement announcement = mock(DeliveryAnnouncement.class);
        when(announcement.getClientId()).thenReturn(client);
        when(announcement.getResponses()).thenReturn(List.of(a, b));
        when(query.findAnnouncementById(tenant, announcementId)).thenReturn(Mono.just(announcement));
        when(query.resolveActorIdForDeliveryPerson(tenant, personA)).thenReturn(Mono.just(actorA));
        when(query.resolveActorIdForDeliveryPerson(tenant, personB)).thenReturn(Mono.just(actorB));
        when(users.findByCoreUserId(any())).thenReturn(Mono.empty());
        when(messages.save(any(NegotiationMessage.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(messages.findByAnnouncementIdAndThreadUserIdOrderByCreatedAtAsc(any(), any())).thenReturn(Flux.empty());
        when(push.sendPushNotification(any(), any(), any(), any())).thenReturn(Mono.empty());
        when(messages.markRead(any(), any(), any(), any())).thenReturn(Mono.just(0L));
        when(messages.countUnread(any(), any(), any())).thenReturn(Mono.just(0L));
        when(messages.findFirstByAnnouncementIdAndThreadUserIdOrderByCreatedAtDesc(any(), any())).thenReturn(Mono.empty());
    }

    private NegotiationMessage saved() {
        ArgumentCaptor<NegotiationMessage> captor = ArgumentCaptor.forClass(NegotiationMessage.class);
        verify(messages).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("the client writes into the thread of the delivery person they name")
    void clientWritesToChosenThread() {
        StepVerifier.create(service.post(announcementId, client, personA, "  Bonjour  "))
                .assertNext(m -> {
                    assertThat(m.senderRole()).isEqualTo("CLIENT");
                    assertThat(m.senderId()).isEqualTo(client);
                    assertThat(m.content()).isEqualTo("Bonjour");
                }).verifyComplete();
        assertThat(saved().getThreadUserId()).isEqualTo(actorA);
    }

    @Test
    @DisplayName("a delivery person writes into their own thread, whoever they pretend to be")
    void freelancerWritesToOwnThread() {
        // the delivery person passes B's id: it is ignored, the thread is the caller's own
        StepVerifier.create(service.post(announcementId, actorA, personB, "Je peux passer à 15h"))
                .assertNext(m -> assertThat(m.senderRole()).isEqualTo("FREELANCER")).verifyComplete();
        assertThat(saved().getThreadUserId()).isEqualTo(actorA);
    }

    @Test
    @DisplayName("a delivery person never reads a competitor's thread")
    void competitorsAreIsolated() {
        StepVerifier.create(service.list(announcementId, actorA, personB)).verifyComplete();
        verify(messages).findByAnnouncementIdAndThreadUserIdOrderByCreatedAtAsc(announcementId, actorA);
        verify(messages, never()).findByAnnouncementIdAndThreadUserIdOrderByCreatedAtAsc(announcementId, actorB);
    }

    @Test
    @DisplayName("the client needs to name a delivery person who really answered")
    void clientMustNameAnAnswerer() {
        StepVerifier.create(service.list(announcementId, client, null)).expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(service.post(announcementId, client, UUID.randomUUID(), "hi")).expectError(AccessDeniedException.class).verify();
        verify(messages, never()).save(any());
    }

    @Test
    @DisplayName("a stranger can neither read nor write, and nothing is stored or read")
    void strangerIsRefused() {
        StepVerifier.create(service.post(announcementId, stranger, personA, "hello")).expectError(AccessDeniedException.class).verify();
        StepVerifier.create(service.list(announcementId, stranger, personA)).expectError(AccessDeniedException.class).verify();
        verify(messages, never()).save(any());
        verify(messages, never()).findByAnnouncementIdAndThreadUserIdOrderByCreatedAtAsc(any(), any());
    }

    @Test
    @DisplayName("blank and oversize messages are rejected before any access check or write")
    void invalidContent() {
        StepVerifier.create(service.post(announcementId, client, personA, "   ")).expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(service.post(announcementId, client, personA, null)).expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(service.post(announcementId, client, personA, "x".repeat(NegotiationChatService.MAX_LENGTH + 1)))
                .expectError(IllegalArgumentException.class).verify();
        verify(messages, never()).save(any());
    }

    @Test
    @DisplayName("an unknown announcement is reported, not treated as allowed")
    void unknownAnnouncement() {
        UUID other = UUID.randomUUID();
        when(query.findAnnouncementById(tenant, other)).thenReturn(Mono.empty());
        StepVerifier.create(service.post(other, client, personA, "hi")).expectError(IllegalArgumentException.class).verify();
    }

    @Test
    @DisplayName("the thread is returned oldest first with the author's name when known")
    void listWithNames() {
        NegotiationMessage first = NegotiationMessage.builder().id(UUID.randomUUID()).announcementId(announcementId)
                .threadUserId(actorA).senderUserId(client).senderRole("CLIENT").content("a")
                .createdAt(Instant.parse("2030-01-01T10:00:00Z")).build();
        NegotiationMessage second = NegotiationMessage.builder().id(UUID.randomUUID()).announcementId(announcementId)
                .threadUserId(actorA).senderUserId(actorA).senderRole("FREELANCER").content("b")
                .createdAt(Instant.parse("2030-01-01T10:01:00Z")).build();
        when(messages.findByAnnouncementIdAndThreadUserIdOrderByCreatedAtAsc(announcementId, actorA)).thenReturn(Flux.just(first, second));
        GofpUser user = new GofpUser();
        user.setFirstName("Jo");
        user.setLastName("Driver");
        when(users.findByCoreUserId(actorA)).thenReturn(Mono.just(user));

        StepVerifier.create(service.list(announcementId, client, personA))
                .assertNext(m -> assertThat(m.content()).isEqualTo("a"))
                .assertNext(m -> {
                    assertThat(m.content()).isEqualTo("b");
                    assertThat(m.senderFirstName()).isEqualTo("Jo");
                })
                .verifyComplete();
    }

    // ── live behaviour ──

    @Test
    @DisplayName("a stored message is pushed live to the conversation, and only to that conversation")
    void postPublishesLive() {
        var inA = new java.util.ArrayList<ChatEvent>();
        var inB = new java.util.ArrayList<ChatEvent>();
        hub.subscribe(announcementId, actorA).subscribe(inA::add);
        hub.subscribe(announcementId, actorB).subscribe(inB::add);

        StepVerifier.create(service.post(announcementId, client, personA, "Bonjour")).expectNextCount(1).verifyComplete();

        assertThat(inA).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo("message");
            assertThat(e.message().content()).isEqualTo("Bonjour");
        });
        assertThat(inB).isEmpty();
    }

    @Test
    @DisplayName("the other party gets a push notification, and a push failure never fails the message")
    void postNotifiesTheOtherParty() {
        StepVerifier.create(service.post(announcementId, client, personA, "Bonjour")).expectNextCount(1).verifyComplete();
        verify(push).sendPushNotification(any(), org.mockito.ArgumentMatchers.eq(actorA), any(), org.mockito.ArgumentMatchers.eq("Bonjour"));

        StepVerifier.create(service.post(announcementId, actorA, null, "Ok")).expectNextCount(1).verifyComplete();
        verify(push).sendPushNotification(any(), org.mockito.ArgumentMatchers.eq(client), any(), org.mockito.ArgumentMatchers.eq("Ok"));

        when(push.sendPushNotification(any(), any(), any(), any())).thenReturn(Mono.error(new IllegalStateException("push down")));
        StepVerifier.create(service.post(announcementId, client, personA, "Encore")).expectNextCount(1).verifyComplete();
    }

    @Test
    @DisplayName("reading emits a receipt only when something was actually marked read")
    void readReceipts() {
        var events = new java.util.ArrayList<ChatEvent>();
        hub.subscribe(announcementId, actorA).subscribe(events::add);

        StepVerifier.create(service.markRead(announcementId, actorA, null)).expectNext(0L).verifyComplete();
        assertThat(events).isEmpty();

        when(messages.markRead(any(), any(), any(), any())).thenReturn(Mono.just(3L));
        StepVerifier.create(service.markRead(announcementId, actorA, null)).expectNext(3L).verifyComplete();
        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo("read");
            assertThat(e.actorId()).isEqualTo(actorA);
            assertThat(e.readAt()).isNotNull();
        });
        // what is marked is the other party's messages: the repository is asked on behalf of the reader
        verify(messages, org.mockito.Mockito.times(2)).markRead(org.mockito.ArgumentMatchers.eq(announcementId),
                org.mockito.ArgumentMatchers.eq(actorA), org.mockito.ArgumentMatchers.eq(actorA), any());
    }

    @Test
    @DisplayName("typing is relayed but never stored, and a stranger cannot signal it")
    void typingIsEphemeral() {
        var events = new java.util.ArrayList<ChatEvent>();
        hub.subscribe(announcementId, actorA).subscribe(events::add);
        StepVerifier.create(service.typing(announcementId, client, personA)).verifyComplete();
        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo("typing");
            assertThat(e.actorId()).isEqualTo(client);
        });
        verify(messages, never()).save(any());
        StepVerifier.create(service.typing(announcementId, stranger, personA)).expectError(AccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("the live stream is delivered after the access check, and refused to strangers")
    void streamIsGuarded() {
        StepVerifier.create(service.stream(announcementId, stranger, personA)).expectError(AccessDeniedException.class).verify();
        StepVerifier.create(service.stream(announcementId, actorB, personA).take(1))
                .then(() -> service.post(announcementId, actorB, null, "mon offre").block())
                .assertNext(e -> assertThat(e.message().content()).isEqualTo("mon offre"))
                .verifyComplete();
        assertThat(hub.openRooms()).isZero();
    }

    @Test
    @DisplayName("the client lists one conversation per answering delivery person, with unread counts; a delivery person only their own")
    void threads() {
        NegotiationMessage last = NegotiationMessage.builder().id(UUID.randomUUID()).announcementId(announcementId)
                .threadUserId(actorA).senderUserId(actorA).senderRole("FREELANCER").content("15h ?")
                .createdAt(Instant.parse("2030-01-01T10:00:00Z")).build();
        when(messages.findFirstByAnnouncementIdAndThreadUserIdOrderByCreatedAtDesc(announcementId, actorA)).thenReturn(Mono.just(last));
        when(messages.countUnread(announcementId, actorA, client)).thenReturn(Mono.just(2L));

        StepVerifier.create(service.threads(announcementId, client).collectList())
                .assertNext(list -> {
                    assertThat(list).hasSize(2);
                    assertThat(list.get(0).deliveryPersonId()).isEqualTo(personA);
                    assertThat(list.get(0).unread()).isEqualTo(2);
                    assertThat(list.get(0).lastMessage().content()).isEqualTo("15h ?");
                    assertThat(list.get(1).deliveryPersonId()).isEqualTo(personB);
                    assertThat(list.get(1).lastMessage()).isNull();
                }).verifyComplete();

        StepVerifier.create(service.threads(announcementId, actorB))
                .assertNext(t -> assertThat(t.deliveryPersonId()).isEqualTo(personB)).verifyComplete();
        StepVerifier.create(service.threads(announcementId, stranger)).expectError(AccessDeniedException.class).verify();
    }
}
