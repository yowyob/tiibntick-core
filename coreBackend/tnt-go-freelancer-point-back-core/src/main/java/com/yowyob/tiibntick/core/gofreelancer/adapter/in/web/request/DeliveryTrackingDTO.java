package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import lombok.Data;

import java.util.UUID;

/**
 * DTO for tracking a delivery, providing coordinates of the delivery person
 * and the relevant locations (pickup and optionally delivery).
 *
 * @author TiiBnTick
 */
@Data
public class DeliveryTrackingDTO {
    private UUID deliveryId;
    private UUID announcementId;
    private UUID deliveryNeedId;
    private UUID freelancerId;
    private DeliveryStatus status;
    
    private Float freelancerLatitude;
    private Float freelancerLongitude;
    
    private Double pickupLatitude;
    private Double pickupLongitude;
    
    private Double deliveryLatitude;
    private Double deliveryLongitude;
}
