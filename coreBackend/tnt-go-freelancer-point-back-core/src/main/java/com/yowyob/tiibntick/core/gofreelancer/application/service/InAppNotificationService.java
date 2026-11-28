package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.kernel.event.application.port.out.ProcessedEventStorePort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IInAppInboxPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.InAppNotification;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.notification.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lot C-36 — the in-app inbox of the three roles (sender, courier, relay owner). Decision D-157: the inbox is held
 * by the Kernel (channel {@code IN_APP}, through tnt-notify-core); this module keeps no notification table. The
 * recipient is always the user of the token: a caller reads and marks only their own inbox, never an id taken from
 * the path.
 *
 * <p>Emission is idempotent (one notification per event key and recipient, recorded in the shared table of processed
 * events) and best-effort: a failed write is logged and never undoes the action that triggered it.
 *
 * <p>Retention and deletion belong to the Kernel: it offers no deletion of an inbox message, so {@link #delete} marks
 * the message read (documented in the Mobile+BFF contract).
 */
@Slf4j
@Service
public class InAppNotificationService {

    public static final int MAX_PAGE = 100;

    /** Scope of the idempotency records in {@code event_bus.processed_events}. */
    static final String IDEMPOTENCY_GROUP = "gofp-in-app";
    static final String IDEMPOTENCY_TOPIC = "in-app-notification";

    private final IInAppInboxPort inbox;
    private final ObjectProvider<ProcessedEventStorePort> processed;

    public InAppNotificationService(IInAppInboxPort inbox, ObjectProvider<ProcessedEventStorePort> processed) {
        this.inbox = inbox;
        this.processed = processed;
    }

    public record Inbox(List<InAppNotification> items, long unread) {}

    // ── emission ─────────────────────────────────────────────────────────

    /**
     * Writes one notification in {@code recipientUserId}'s Kernel inbox, unless this event already produced one for
     * them. Never fails: errors are logged.
     */
    public Mono<Void> emit(UUID recipientUserId, NotificationType type, String eventKey, String title,
                           String message, Map<String, String> params, String linkKind, UUID linkId) {
        if (recipientUserId == null || type == null || eventKey == null) {
            return Mono.empty();
        }
        String key = eventKey + ":" + recipientUserId;
        // event_id is at most 128 characters: a name-based UUID of the key, the key itself travels as metadata
        String eventId = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        ProcessedEventStorePort store = processed.getIfAvailable();
        Mono<Boolean> already = store == null ? Mono.just(false)
                : store.isProcessed(IDEMPOTENCY_GROUP, IDEMPOTENCY_TOPIC, eventId).defaultIfEmpty(false);
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(IInAppInboxPort.META_TYPE, type.name());
        metadata.put(IInAppInboxPort.META_EVENT_KEY, key);
        if (linkKind != null) metadata.put(IInAppInboxPort.META_LINK_KIND, linkKind);
        if (linkId != null) metadata.put(IInAppInboxPort.META_LINK_ID, linkId.toString());
        String json = toJson(params);
        if (json != null) metadata.put(IInAppInboxPort.META_PARAMS, json);
        return already
                .flatMap(done -> Boolean.TRUE.equals(done) ? Mono.<Void>empty()
                        : inbox.send(recipientUserId, title, message, metadata)
                                // recorded only once the Kernel accepted the message: a failed send is retried
                                .then(Mono.defer(() -> store == null ? Mono.<Void>empty()
                                        : store.markProcessed(IDEMPOTENCY_GROUP, IDEMPOTENCY_TOPIC, eventId))))
                .onErrorResume(e -> {
                    log.warn("In-app notification {} for {} not sent: {}", key, recipientUserId, e.getMessage());
                    return Mono.empty();
                });
    }

    // ── the caller's inbox ───────────────────────────────────────────────

    /** The caller's notifications, unread first then newest first, one page; {@code unread} counts them all. */
    public Mono<Inbox> mine(UUID callerId, int page, int size) {
        UUID me = requireCaller(callerId);
        int limit = Math.max(1, Math.min(MAX_PAGE, size));
        long offset = (long) Math.max(0, page) * limit;
        return all(me).collectList().map(list -> {
            List<InAppNotification> sorted = list.stream()
                    .sorted(Comparator.comparing((InAppNotification n) -> n.readAt() != null)
                            .thenComparing(InAppNotification::createdAt,
                                    Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            long unread = sorted.stream().filter(n -> n.readAt() == null).count();
            return new Inbox(sorted.stream().skip(offset).limit(limit).toList(), unread);
        });
    }

    public Mono<InAppNotification> markRead(UUID notificationId, UUID callerId) {
        return own(notificationId, callerId).flatMap(n -> n.readAt() != null ? Mono.just(n)
                : inbox.markRead(n.id()).onErrorMap(InAppNotificationService::unavailable));
    }

    /** Marks every unread notification of the caller; emits how many were marked. */
    public Mono<Long> markAllRead(UUID callerId) {
        UUID me = requireCaller(callerId);
        return all(me).filter(n -> n.readAt() == null)
                .concatMap(n -> inbox.markRead(n.id()).onErrorMap(InAppNotificationService::unavailable))
                .count();
    }

    /**
     * The Kernel inbox has no deletion: the caller's notification is marked read (it leaves the unread count; the app
     * hides it). 404 for an unknown notification and for someone else's.
     */
    public Mono<Void> delete(UUID notificationId, UUID callerId) {
        return markRead(notificationId, callerId).then();
    }

    /** 404 for an unknown notification <em>and</em> for someone else's: nothing is learnt about it. */
    private Mono<InAppNotification> own(UUID notificationId, UUID callerId) {
        UUID me = requireCaller(callerId);
        return all(me).filter(n -> n.id() != null && n.id().equals(notificationId)).next()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Notification not found: " + notificationId)));
    }

    /** The caller's Go messages; a message addressed to someone else (never expected) is left out. */
    private Flux<InAppNotification> all(UUID me) {
        return inbox.mine()
                .filter(n -> n.recipientUserId() == null || me.equals(n.recipientUserId()))
                .onErrorMap(InAppNotificationService::unavailable);
    }

    private static Throwable unavailable(Throwable e) {
        if (e instanceof ResponseStatusException) return e;
        log.warn("Kernel in-app inbox unavailable: {}", e.getMessage());
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "In-app inbox unavailable", e);
    }

    private static UUID requireCaller(UUID callerId) {
        if (callerId == null) {
            throw new AccessDeniedException("An authenticated user is required");
        }
        return callerId;
    }

    // ── helpers ──────────────────────────────────────────────────────────

    static Map<String, String> params(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] != null && !kv[i + 1].isBlank()) {
                m.put(kv[i], kv[i + 1]);
            }
        }
        return m;
    }

    static String toJson(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        params.forEach((k, v) -> {
            if (sb.length() > 1) sb.append(',');
            sb.append(quote(k)).append(':').append(quote(v));
        });
        return sb.append('}').toString();
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
