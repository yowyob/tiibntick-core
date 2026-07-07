package com.yowyob.tiibntick.core.delivery.domain.model.enums;

/**
 * Type of delivery-related notification sent to users.
 *
 * @author MANFOUO Braun
 */
public enum NotificationType {

    REGISTERED_PARCEL,
    PARCEL_IN_TRANSIT,
    PARCEL_DELIVERED,
    DELAYED_PARCEL,
    DRIVER_ASSIGNED,
    DELIVERY_FAILED,
    ANNOUNCEMENT_RESPONSE_RECEIVED,
    RELAY_POINT_DEPOSIT,
    RELAY_POINT_PICKUP
}
