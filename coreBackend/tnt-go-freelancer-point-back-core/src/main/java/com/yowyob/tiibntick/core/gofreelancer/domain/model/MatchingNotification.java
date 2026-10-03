package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import java.util.UUID;

/**
 * A matching notification pushed in real time to one freelancer's SSE stream.
 *
 * <p>Lot C-21.2 — the stream's own type, so that neither the {@code NotificationStreamPort}
 * consumers nor the web controller depend on the Kafka event
 * {@code adapter.out.kafka.event.MatchingNotificationEvent}; the Kafka consumer maps the
 * event into this on the way in.
 */
public record MatchingNotification(UUID freelancerId, UUID announcementId, String title, String message) {
}
