package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Represents a notification related to an announcement.
 * Extends the concept of Notification for announcement-specific notifications.
 *
 * @author Kengfack Lagrange
 * @date 21/01/2026
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("notification_annonces")
public class NotificationAnnonce {

    @Id
    @Column("id")
    private UUID id;

    @NotNull
    @Column("announcement_id")
    private UUID announcementId;
}
