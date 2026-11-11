package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.GofpUserR2dbcRepository;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.NegotiationMessageRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.application.service.NegotiationChatHub.ChatEvent;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.NegotiationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Negotiation between the client of an announcement and one delivery person who answered it.
 *
 * <p>A thread is the pair (announcement, delivery person): the client opens one per answering
 * delivery person, and a delivery person only ever sees their own, never the offers of competitors.
 * The author is always the authenticated caller, never a body field.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NegotiationChatService {

    public static final String CLIENT = "CLIENT";
    public static final String FREELANCER = "FREELANCER";
    public static final int MAX_LENGTH = 2000;

    /** A message as exposed to the API, with the author's display name. */
    public record ChatMessage(UUID id, UUID announcementId, UUID senderId, String senderRole,
                              String senderFirstName, String senderLastName, String content, Instant createdAt,
                              Instant readAt) {}

    /** One conversation of an announcement, as listed to its participants. */
    public record ChatThread(UUID deliveryPersonId, UUID threadUserId, ChatMessage lastMessage, long unread) {}

    /** Who the caller is on this announcement and which conversation they are looking at. */
    record Access(String role, UUID threadUserId, UUID otherPartyUserId) {}

    private final DeliveryQueryUseCase deliveryQuery;
    private final NegotiationMessageRepository messages;
    private final GofpUserR2dbcRepository users;
    private final TenantContextHolder tenantContextHolder;
    private final NegotiationChatHub hub;
    private final PushNotificationPort push;

    /**
     * @param withDeliveryPerson the delivery person (as in the announcement's responses) the client wants to talk
     *                           to; required for the client, ignored for a delivery person (always their own thread)
     */
    Mono<Access> access(UUID tenantId, UUID announcementId, UUID caller, UUID withDeliveryPerson) {
        return deliveryQuery.findAnnouncementById(tenantId, announcementId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Announcement not found: " + announcementId)))
                .flatMap(announcement -> {
                    if (caller.equals(announcement.getClientId())) {
                        return clientAccess(tenantId, announcement, withDeliveryPerson);
                    }
                    return answeringAccess(tenantId, announcement, caller);
                });
    }

    private Mono<Access> clientAccess(UUID tenantId, DeliveryAnnouncement announcement, UUID withDeliveryPerson) {
        if (withDeliveryPerson == null) {
            return Mono.error(new IllegalArgumentException("deliveryPersonId is required to open a conversation"));
        }
        boolean answered = announcement.getResponses().stream()
                .map(AnnouncementResponse::getDeliveryPersonId)
                .anyMatch(withDeliveryPerson::equals);
        if (!answered) {
            return Mono.error(new AccessDeniedException("This delivery person has not answered the announcement"));
        }
        return actorOf(tenantId, withDeliveryPerson).map(actor -> new Access(CLIENT, actor, actor));
    }

    private Mono<Access> answeringAccess(UUID tenantId, DeliveryAnnouncement announcement, UUID caller) {
        return Flux.fromIterable(announcement.getResponses())
                .map(AnnouncementResponse::getDeliveryPersonId)
                .concatMap(person -> actorOf(tenantId, person).map(caller::equals))
                .any(Boolean::booleanValue)
                .flatMap(answering -> answering
                        ? Mono.just(new Access(FREELANCER, caller, announcement.getClientId()))
                        : Mono.<Access>error(new AccessDeniedException(
                                "Only the client and the delivery persons who answered may use this conversation")));
    }

    /** User id behind a delivery person id; an id that cannot be resolved is its own (never matches a caller). */
    private Mono<UUID> actorOf(UUID tenantId, UUID deliveryPersonId) {
        return deliveryQuery.resolveActorIdForDeliveryPerson(tenantId, deliveryPersonId)
                .onErrorResume(e -> {
                    log.warn("Cannot resolve the actor of delivery person {}: {}", deliveryPersonId, e.getMessage());
                    return Mono.just(deliveryPersonId);
                });
    }

    public Flux<ChatMessage> list(UUID announcementId, UUID caller, UUID withDeliveryPerson) {
        return tenantContextHolder.currentTenantId()
                .defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT)
                .flatMap(tenant -> access(tenant, announcementId, caller, withDeliveryPerson))
                .flatMapMany(access -> Flux.defer(() ->
                        messages.findByAnnouncementIdAndThreadUserIdOrderByCreatedAtAsc(announcementId, access.threadUserId())))
                .concatMap(this::toChatMessage);
    }

    public Mono<ChatMessage> post(UUID announcementId, UUID caller, UUID withDeliveryPerson, String content) {
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            return Mono.error(new IllegalArgumentException("content must not be blank"));
        }
        if (text.length() > MAX_LENGTH) {
            return Mono.error(new IllegalArgumentException("content must not exceed " + MAX_LENGTH + " characters"));
        }
        return tenantContextHolder.currentTenantId()
                .defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT)
                .flatMap(tenant -> access(tenant, announcementId, caller, withDeliveryPerson))
                .flatMap(access -> messages.save(NegotiationMessage.builder()
                                .id(UUID.randomUUID())
                                .announcementId(announcementId)
                                .threadUserId(access.threadUserId())
                                .senderUserId(caller)
                                .senderRole(access.role())
                                .content(text)
                                .createdAt(Instant.now())
                                .build())
                        .flatMap(this::toChatMessage)
                        .doOnNext(m -> hub.publish(announcementId, access.threadUserId(), ChatEvent.message(m)))
                        .flatMap(m -> notifyOtherParty(access, m).thenReturn(m)));
    }

    /** A push is a courtesy: its failure never fails the message that was stored. */
    private Mono<Void> notifyOtherParty(Access access, ChatMessage m) {
        if (access.otherPartyUserId() == null) {
            return Mono.empty();
        }
        String who = ((m.senderFirstName() != null ? m.senderFirstName() : "") + " "
                + (m.senderLastName() != null ? m.senderLastName() : "")).strip();
        String preview = m.content().length() > 80 ? m.content().substring(0, 80) + "…" : m.content();
        return tenantContextHolder.currentTenantId()
                .defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT)
                .flatMap(tenant -> push.sendPushNotification(tenant, access.otherPartyUserId(),
                        "Nouveau message" + (who.isEmpty() ? "" : " de " + who), preview))
                .onErrorResume(e -> {
                    log.warn("Chat push failed: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /** Marks the other party's messages as read and tells the thread (read receipts). */
    public Mono<Long> markRead(UUID announcementId, UUID caller, UUID withDeliveryPerson) {
        return tenantContextHolder.currentTenantId()
                .defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT)
                .flatMap(tenant -> access(tenant, announcementId, caller, withDeliveryPerson))
                .flatMap(access -> {
                    Instant now = Instant.now();
                    return messages.markRead(announcementId, access.threadUserId(), caller, now)
                            .doOnNext(n -> {
                                if (n > 0) {
                                    hub.publish(announcementId, access.threadUserId(), ChatEvent.read(caller, now));
                                }
                            });
                });
    }

    /** Ephemeral "is typing" signal: never stored. */
    public Mono<Void> typing(UUID announcementId, UUID caller, UUID withDeliveryPerson) {
        return tenantContextHolder.currentTenantId()
                .defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT)
                .flatMap(tenant -> access(tenant, announcementId, caller, withDeliveryPerson))
                .doOnNext(access -> hub.publish(announcementId, access.threadUserId(), ChatEvent.typing(caller)))
                .then();
    }

    /**
     * Opens the live events of one conversation. The access check is the Mono itself: it errors before any
     * stream exists, so a caller who is refused gets a clean 403 instead of a half-open response.
     */
    public Mono<Flux<ChatEvent>> openStream(UUID announcementId, UUID caller, UUID withDeliveryPerson) {
        return tenantContextHolder.currentTenantId()
                .defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT)
                .flatMap(tenant -> access(tenant, announcementId, caller, withDeliveryPerson))
                .map(access -> hub.subscribe(announcementId, access.threadUserId()));
    }

    /** Same as {@link #openStream} for callers that only want the events. */
    public Flux<ChatEvent> stream(UUID announcementId, UUID caller, UUID withDeliveryPerson) {
        return openStream(announcementId, caller, withDeliveryPerson).flatMapMany(f -> f);
    }

    /**
     * The conversations the caller takes part in on this announcement: the client sees one per answering
     * delivery person, a delivery person sees only their own.
     */
    public Flux<ChatThread> threads(UUID announcementId, UUID caller) {
        return tenantContextHolder.currentTenantId()
                .defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT)
                .flatMapMany(tenant -> deliveryQuery.findAnnouncementById(tenant, announcementId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Announcement not found: " + announcementId)))
                        .flatMapMany(announcement -> {
                            boolean isClient = caller.equals(announcement.getClientId());
                            return Flux.fromIterable(announcement.getResponses())
                                    .map(AnnouncementResponse::getDeliveryPersonId)
                                    .distinct()
                                    .concatMap(person -> actorOf(tenant, person).map(actor -> new UUID[] {person, actor}))
                                    .filter(pair -> isClient || pair[1].equals(caller))
                                    .concatMap(pair -> threadSummary(announcementId, pair[0], pair[1], caller))
                                    .switchIfEmpty(isClient ? Flux.empty() : Flux.error(new AccessDeniedException(
                                            "Only the client and the delivery persons who answered may use this conversation")));
                        }));
    }

    private Mono<ChatThread> threadSummary(UUID announcementId, UUID person, UUID threadUser, UUID reader) {
        Mono<ChatMessage> last = messages.findFirstByAnnouncementIdAndThreadUserIdOrderByCreatedAtDesc(announcementId, threadUser)
                .flatMap(this::toChatMessage);
        return Mono.zip(
                        last.map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()),
                        messages.countUnread(announcementId, threadUser, reader).defaultIfEmpty(0L))
                .map(t -> new ChatThread(person, threadUser, t.getT1().orElse(null), t.getT2()));
    }

    private Mono<ChatMessage> toChatMessage(NegotiationMessage m) {
        return users.findByCoreUserId(m.getSenderUserId())
                .map(u -> new String[] {u.getFirstName(), u.getLastName()})
                .defaultIfEmpty(new String[] {null, null})
                .map(name -> new ChatMessage(m.getId(), m.getAnnouncementId(), m.getSenderUserId(), m.getSenderRole(),
                        name[0], name[1], m.getContent(), m.getCreatedAt(), m.getReadAt()));
    }
}
