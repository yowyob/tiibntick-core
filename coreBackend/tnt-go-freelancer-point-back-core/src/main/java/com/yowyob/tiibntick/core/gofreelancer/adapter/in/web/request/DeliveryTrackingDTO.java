package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import lombok.Data;

import java.time.Instant;
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
    /** ISO 8601 timestamp of the last GPS ping received from the deliverer. Null when presence is absent. */
    private Instant freelancerPositionAt;
    
    private Double pickupLatitude;
    private Double pickupLongitude;
    
    private Double deliveryLatitude;
    private Double deliveryLongitude;
}
