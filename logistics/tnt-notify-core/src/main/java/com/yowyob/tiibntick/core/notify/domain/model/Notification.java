package com.yowyob.tiibntick.core.notify.domain.model;

import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;
import com.yowyob.tiibntick.core.notify.domain.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationId;
import lombok.Getter;

import java.time.Instant;

/**
 * Aggregate root representing an outbound notification.
 * Tracks the full lifecycle: PENDING → SENT | FAILED.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
@Getter
public class Notification {

    private final NotificationId id;
    private final String recipientId;
    private final NotificationChannel channel;
    private final String content;
    private final NotificationPriority priority;
    private DeliveryStatus status;
    private final Instant createdAt;
    private Instant sentAt;
    private String errorMessage;
    private int attempts;

    /**
     * Creation constructor — sets default status and priority.
     */
    public Notification(NotificationId id,
            String recipientId,
            NotificationChannel channel,
            String content) {
        this(id, recipientId, channel, content, NotificationPriority.NORMAL);
    }

    /**
     * Creation constructor with explicit priority.
     */
    public Notification(NotificationId id,
            String recipientId,
            NotificationChannel channel,
            String content,
            NotificationPriority priority) {
        this.id = id;
        this.recipientId = recipientId;
        this.channel = channel;
        this.content = content;
        this.priority = priority;
        this.status = DeliveryStatus.PENDING;
        this.createdAt = Instant.now();
        this.attempts = 0;
    }

    /**
     * Full reconstitution constructor (from persistence).
     */
    public Notification(NotificationId id,
            String recipientId,
            NotificationChannel channel,
            String content,
            NotificationPriority priority,
            DeliveryStatus status,
            Instant createdAt,
            Instant sentAt,
            String errorMessage,
            int attempts) {
        this.id = id;
        this.recipientId = recipientId;
        this.channel = channel;
        this.content = content;
        this.priority = priority;
        this.status = status;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
        this.errorMessage = errorMessage;
        this.attempts = attempts;
    }

    // ── Domain behaviour ─────────────────────────────────────────────────────

    public void markAsSent() {
        this.status = DeliveryStatus.SENT;
        this.sentAt = Instant.now();
    }

    public void markAsFailed(String reason) {
        this.status = DeliveryStatus.FAILED;
        this.errorMessage = reason;
        this.attempts++;
    }

    public boolean canBeRetried() {
        return this.status == DeliveryStatus.FAILED && this.attempts < 3;
    }
}
