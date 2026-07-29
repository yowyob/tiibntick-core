package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryUrgency;
import java.time.Instant;
import lombok.Data;

/**
 * DTO for updating delivery details (tarif, urgency, time windows, etc.).
 *
 * @author François-Charles ATANGA
 * @date 07/07/2026
 */
@Data
public class DeliveryUpdateDTO {
    private Double tarif;
    private DeliveryUrgency urgency;
    private Instant pickupMinTime;
    private Instant pickupMaxTime;
    private Instant deliveryMinTime;
    private Instant deliveryMaxTime;
    private Instant estimatedDelivery;
    private Double noteLivreur;
    private Double deliveryNote;
}
