package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.NotificationRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Notification;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.notification.NotificationStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.notification.NotificationType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Lot C-36 — the in-app inbox of the three roles (sender, courier, relay owner), stored in the gofp
 * {@code notifications} table. The recipient ({@code person_id}) is always the user id of the token:
 * a caller reads, marks and deletes only their own notifications, never an id taken from the path.
 *
 * <p>Emission is idempotent (one notification per event key and recipient) and best-effort: a failed
 * write is logged and never undoes the action that triggered it.
 *
 * <p>Retention: read notifications older than {@code read-days} (30) and every notification older
 * than {@code all-days} (90) are deleted by a scheduled, idempotent purge.
 */
@Slf4j
@Service
public class InAppNotificationService {

    public static final int MAX_PAGE = 100;

    private final NotificationRepository repository;
    private final Duration readRetention;
    private final Duration allRetention;
    private Clock clock = Clock.systemUTC();

    public InAppNotificationService(NotificationRepository repository,
                                    @Value("${tnt.gofp.notifications.retention.read-days:30}") long readDays,
                                    @Value("${tnt.gofp.notifications.retention.all-days:90}") long allDays) {
        this.repository = repository;
        this.readRetention = Duration.ofDays(readDays);
        this.allRetention = Duration.ofDays(allDays);
    }

    /** Tests only. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    public record Inbox(List<Notification> items, long unread) {}

    // ── emission ─────────────────────────────────────────────────────────

    /**
     * Stores one notification for {@code recipientUserId}, unless this event already produced one
     * for them. Never fails: errors are logged.
     */
    public Mono<Void> emit(UUID recipientUserId, NotificationType type, String eventKey, String title,
                           String message, Map<String, String> params, String linkKind, UUID linkId) {
        if (recipientUserId == null || type == null || eventKey == null) {
            return Mono.empty();
        }
        String key = eventKey + ":" + recipientUserId;
        return repository.existsByEventKey(key)
                .defaultIfEmpty(false)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.<Void>empty();
                    }
                    Notification n = new Notification();
                    n.setPersonId(recipientUserId);
                    n.setNotificationType(type);
                    n.setTitle(title);
                    n.setMessage(message);
                    n.setNotificationStatus(NotificationStatus.SENT);
                    n.setCreatedAt(clock.instant());
                    n.setEventKey(key);
                    n.setLinkKind(linkKind);
                    n.setLinkId(linkId);
                    n.setParams(toJson(params));
                    return repository.save(n).then();
                })
                .onErrorResume(e -> {
                    log.warn("In-app notification {} for {} not stored: {}", key, recipientUserId, e.getMessage());
                    return Mono.empty();
                });
    }

    // ── the caller's inbox ───────────────────────────────────────────────

    public Mono<Inbox> mine(UUID callerId, int page, int size) {
        UUID me = requireCaller(callerId);
        int limit = Math.max(1, Math.min(MAX_PAGE, size));
        long offset = (long) Math.max(0, page) * limit;
        return repository.findInbox(me, limit, offset).collectList()
                .zipWith(repository.countUnread(me).defaultIfEmpty(0L))
                .map(t -> new Inbox(t.getT1(), t.getT2()));
    }

    public Mono<Notification> markRead(UUID notificationId, UUID callerId) {
        return own(notificationId, callerId).flatMap(n -> {
            if (n.getReadAt() != null) {
                return Mono.just(n);
            }
            n.setReadAt(clock.instant());
            n.setNotificationStatus(NotificationStatus.READ);
            return repository.save(n);
        });
    }

    public Mono<Long> markAllRead(UUID callerId) {
        return repository.markAllRead(requireCaller(callerId), clock.instant()).defaultIfEmpty(0L);
    }

    public Mono<Void> delete(UUID notificationId, UUID callerId) {
        return own(notificationId, callerId).flatMap(n -> repository.deleteById(n.getId()));
    }

    /** 404 for an unknown notification <em>and</em> for someone else's: nothing is learnt about it. */
    private Mono<Notification> own(UUID notificationId, UUID callerId) {
        UUID me = requireCaller(callerId);
        return repository.findById(notificationId)
                .filter(n -> me.equals(n.getPersonId()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Notification not found: " + notificationId)));
    }

    private static UUID requireCaller(UUID callerId) {
        if (callerId == null) {
            throw new AccessDeniedException("An authenticated user is required");
        }
        return callerId;
    }

    // ── retention ────────────────────────────────────────────────────────

    /** Deletes read notifications past the read retention and all notifications past the full one. */
    public Mono<Long> purge() {
        Instant now = clock.instant();
        return repository.deleteExpired(now.minus(readRetention), now.minus(allRetention)).defaultIfEmpty(0L);
    }

    @Scheduled(cron = "${tnt.gofp.notifications.retention.cron:0 17 3 * * *}")
    void scheduledPurge() {
        purge().doOnNext(n -> log.info("In-app notifications purged: {}", n))
                .onErrorResume(e -> {
                    log.warn("In-app notification purge failed: {}", e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
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
