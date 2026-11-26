package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.notification.NotificationStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.notification.NotificationType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Represents a notification sent to a user.
 * Notifications inform users about parcel and delivery status updates.
 *
 * @author Kengfack Lagrange
 * @date 21/01/2026
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("notifications")
public class Notification {

    @Id
    @Column("id")
    private UUID id;

    @NotNull
    @Column("person_id")
    private UUID personId;

    @NotNull
    @Column("notification_type")
    private NotificationType notificationType;

    @NotNull
    @Column("title")
    private String title;

    @NotNull
    @Column("message")
    private String message;

    @NotNull
    @Column("notification_status")
    private NotificationStatus notificationStatus;

    // ── Lot C-36 — in-app inbox (person_id = recipient's user id) ─────────────

    @Column("created_at")
    private Instant createdAt;

    /** Null while unread. */
    @Column("read_at")
    private Instant readAt;

    /** One notification per event and recipient: a replayed action never creates a second one. */
    @Column("event_key")
    private String eventKey;

    /** Screen the app opens on tap: SHIPMENT (sender), COURSE (courier), RELAY_PARCEL (relay). */
    @Column("link_kind")
    private String linkKind;

    @Column("link_id")
    private UUID linkId;

    /** JSON object of the values the app renders in the user's language (name, price, ref...). */
    @Column("params")
    private String params;
}
