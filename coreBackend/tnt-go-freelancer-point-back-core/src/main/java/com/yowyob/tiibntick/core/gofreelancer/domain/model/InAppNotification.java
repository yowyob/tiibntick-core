package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.notification.NotificationType;

import java.time.Instant;
import java.util.UUID;

/**
 * Lot C-36 — one notification of a user's in-app inbox, as the app shows it. Not persisted by this module: it is a
 * message of the Kernel inbox (decision D-157), {@code id} being the Kernel delivery id.
 *
 * @param params JSON object of the values the app renders in the user's language (name, price, ref...), or null
 * @param readAt null while unread
 */
public record InAppNotification(UUID id, UUID recipientUserId, NotificationType type, String title, String message,
                                String params, String linkKind, UUID linkId, Instant createdAt, Instant readAt) {
}
