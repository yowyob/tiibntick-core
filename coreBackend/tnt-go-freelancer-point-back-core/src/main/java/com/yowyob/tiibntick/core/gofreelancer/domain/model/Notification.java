package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.notification.NotificationStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.notification.NotificationType;
import jakarta.validation.constraints.NotNull;
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
}
