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
 * Represents a notification related to a delivery.
 * Extends the concept of Notification for delivery-specific notifications.
 *
 * @author Kengfack Lagrange
 * @date 21/01/2026
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("notification_livraisons")
public class NotificationLivraison {

    @Id
    @Column("id")
    private UUID id;

    @NotNull
    @Column("delivery_id")
    private UUID deliveryId;
}
