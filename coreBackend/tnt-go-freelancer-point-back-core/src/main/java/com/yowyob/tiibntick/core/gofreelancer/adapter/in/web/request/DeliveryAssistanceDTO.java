package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * DTO providing navigation and assistance information for the delivery person.
 *
 * @author François-Charles ATANGA
 */
@Data
@Builder
public class DeliveryAssistanceDTO {
    private UUID deliveryId;
    private DeliveryStatus currentStatus;
    private String stepDescription;
    
    private Double currentLatitude;
    private Double currentLongitude;
    
    private Double targetLatitude;
    private Double targetLongitude;
    
    private Double distanceKm;
    private Integer estimatedTimeMinutes; // Estimation in minutes
}
