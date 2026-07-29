package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryUrgency;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/**
 * DTO representing the response for a delivery.
 *
 * @author François-Charles ATANGA
 * @date 07/07/2026
 */
@Data
public class DeliveryResponseDTO {
    private UUID id;
    private UUID announcementId;
    private UUID deliveryNeedId;
    private UUID freelancerId;
    private DeliveryStatus status;
    private DeliveryUrgency urgency;
    private Double tarif;
    private Double noteLivreur;
    private Instant pickupMinTime;
    private Instant pickupMaxTime;
    private Instant deliveryMinTime;
    private Instant deliveryMaxTime;
    private Instant estimatedDelivery;
    private Integer duration;
    private Double deliveryNote;
    private Double distanceKm;

    // Enriched fields from related entities
    private String announcementTitle;
    private String freelancerFirstName;
    private String freelancerLastName;
    private String freelancerPhone;
}
