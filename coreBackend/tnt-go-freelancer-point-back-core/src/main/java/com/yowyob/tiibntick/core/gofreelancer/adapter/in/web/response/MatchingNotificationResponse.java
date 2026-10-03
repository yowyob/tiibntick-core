package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.MatchingNotification;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * SSE payload of {@code GET /api/notifications/stream/{freelancerId}} (event {@code notification}).
 *
 * <p>Lot C-21.2 — wire contract consumed by the mobile app: same field names, declaration order
 * and Lombok shape as the Kafka event it replaces in the controller signature, so the JSON is
 * byte-for-byte identical under both the app's field-visibility mapper and Spring's defaults
 * (pinned by {@code NotificationStreamControllerSseContractTest}). Do not reorder the fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchingNotificationResponse {
    private UUID freelancerId;
    private UUID announcementId;
    private String title;
    private String message;

    public static MatchingNotificationResponse from(MatchingNotification notification) {
        return new MatchingNotificationResponse(notification.freelancerId(), notification.announcementId(),
                notification.title(), notification.message());
    }
}
