package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;

/**
 * Status of a Go delivery need, derived from what delivery-core holds for it. A need has no state of
 * its own: the announcement published for it and, once a response is selected, the delivery created
 * from that announcement tell where it stands.
 *
 * <pre>
 * announcement          delivery        need
 * (none)                -               PENDING
 * DRAFT/PUBLISHED/IN_NEGOTIATION -      PENDING
 * CANCELLED             -               CANCELLED
 * COMPLETED             -               COMPLETED (lot evt-l2, QO-9: also when the delivery FAILED for good)
 * ASSIGNED              unknown         ASSIGNED
 * ASSIGNED              CREATED, ASSIGNED   ASSIGNED
 * ASSIGNED              PICKED_UP, IN_TRANSIT, AT_RELAY_HUB, OUT_FOR_DELIVERY, DELIVERY_ATTEMPT_FAILED,
 *                       FAILED (until the announcement is completed), INCIDENT_* , PAUSED_BY_INCIDENT   IN_PROGRESS
 * ASSIGNED              DELIVERED       COMPLETED
 * ASSIGNED              CANCELLED       CANCELLED
 * </pre>
 *
 * {@link DeliveryNeedStatus#ACCEPTED} is never derived: nothing in the product ever produced it.
 */
public final class DeliveryNeedStatusPolicy {

    private DeliveryNeedStatusPolicy() {
    }

    public static DeliveryNeedStatus derive(AnnouncementStatus announcement, DeliveryStatus delivery) {
        if (announcement == null) {
            return DeliveryNeedStatus.PENDING;
        }
        return switch (announcement) {
            case DRAFT, PUBLISHED, IN_NEGOTIATION -> DeliveryNeedStatus.PENDING;
            case CANCELLED -> DeliveryNeedStatus.CANCELLED;
            case COMPLETED -> DeliveryNeedStatus.COMPLETED;
            case ASSIGNED -> fromDelivery(delivery);
        };
    }

    /**
     * QO-9: outcome of a completed need — {@code DELIVERED} or {@code FAILED} — read from the delivery; {@code null}
     * when the need is not {@code COMPLETED} or the delivery is unknown.
     */
    public static String outcome(AnnouncementStatus announcement, DeliveryStatus delivery) {
        if (derive(announcement, delivery) != DeliveryNeedStatus.COMPLETED || delivery == null) {
            return null;
        }
        return switch (delivery) {
            case DELIVERED -> "DELIVERED";
            case FAILED -> "FAILED";
            default -> null;
        };
    }

    private static DeliveryNeedStatus fromDelivery(DeliveryStatus delivery) {
        if (delivery == null) {
            return DeliveryNeedStatus.ASSIGNED;
        }
        return switch (delivery) {
            case CREATED, ASSIGNED -> DeliveryNeedStatus.ASSIGNED;
            case DELIVERED -> DeliveryNeedStatus.COMPLETED;
            case CANCELLED -> DeliveryNeedStatus.CANCELLED;
            default -> DeliveryNeedStatus.IN_PROGRESS;
        };
    }
}
