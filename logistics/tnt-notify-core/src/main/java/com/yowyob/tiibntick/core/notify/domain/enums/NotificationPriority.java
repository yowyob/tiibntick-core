package com.yowyob.tiibntick.core.notify.domain.enums;

/**
 * Priority levels for notifications.
 * Influences delivery order and retry strategy in the notification engine.
 *
 * @author MANFOUO Braun
 */
public enum NotificationPriority {

    /**
     * Urgent alerts (blockchain fraud detected, dispute opened). Sent immediately.
     */
    CRITICAL,

    /**
     * Delivery status changes, payment confirmations. Sent within seconds.
     */
    HIGH,

    /**
     * Standard informational messages. Sent within minutes.
     */
    NORMAL,

    /**
     * Promotional or informational content. Can be batched.
     */
    LOW
}
