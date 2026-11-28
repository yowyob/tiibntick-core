package com.yowyob.tiibntick.core.delivery.application.port.out;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Delivery thresholds read at the time of a transition (spec §5, lot L1): maximum attempts, failed returns, relay
 * storage and its reminder. Values come from the administration module (platform value, organisation override)
 * and are cached 60 s, the cache being invalidated by {@code DeliveryThresholdsChanged}.
 */
public interface DeliveryThresholdsPort {

    /** Thresholds that apply to one delivery. */
    record Thresholds(int maxDeliveryAttempts, int maxFailedReturns, int relayStorageReminderDaysBefore,
                      Alerts alerts) {

        public Thresholds {
            alerts = alerts == null ? Alerts.DEFAULT : alerts;
        }

        /** Thresholds with the platform default alert values. */
        public Thresholds(int maxDeliveryAttempts, int maxFailedReturns, int relayStorageReminderDaysBefore) {
            this(maxDeliveryAttempts, maxFailedReturns, relayStorageReminderDaysBefore, Alerts.DEFAULT);
        }
    }

    /**
     * Alert thresholds of a delivery (QO-18, D-131, D-132): set by the TiiBnTick administrator, overridable by an Agency
     * organisation (D-133), like the other delivery thresholds.
     *
     * @param pickupLateGrace           {@code PICKUP_LATE}: after the end of the pickup window
     * @param deliveryLateGrace         {@code DELIVERY_LATE}: after the end of the delivery window
     * @param deliveryLateFallbackStandard {@code DELIVERY_LATE} without a window, urgency STANDARD: after creation
     * @param deliveryLateFallbackExpress  idem, urgency EXPRESS
     * @param deliveryLateFallbackSameDay  idem, urgency SAME_DAY
     * @param etaExceededGrace          {@code ETA_EXCEEDED}: after the announced ETA
     * @param gpsSignalLost             {@code GPS_SIGNAL_LOST}: without a position
     * @param prolongedStop             {@code PROLONGED_STOP}: stopped
     */
    record Alerts(Duration pickupLateGrace, Duration deliveryLateGrace, Duration deliveryLateFallbackStandard,
                  Duration deliveryLateFallbackExpress, Duration deliveryLateFallbackSameDay, Duration etaExceededGrace,
                  Duration gpsSignalLost, Duration prolongedStop) {

        /** Platform defaults (administration {@code DeliveryThreshold} defaults). */
        public static final Alerts DEFAULT = new Alerts(Duration.ofMinutes(15), Duration.ZERO, Duration.ofHours(48),
                Duration.ofHours(24), Duration.ofHours(8), Duration.ofMinutes(30), Duration.ofMinutes(10),
                Duration.ofMinutes(20));

        /**
         * Delay after creation when the delivery has no window (D-132). A scheduled delivery always has a window; if
         * it has none, the standard delay applies.
         */
        public Duration deliveryLateFallback(DeliveryUrgency urgency) {
            if (urgency == DeliveryUrgency.EXPRESS) {
                return deliveryLateFallbackExpress;
            }
            if (urgency == DeliveryUrgency.SAME_DAY) {
                return deliveryLateFallbackSameDay;
            }
            return deliveryLateFallbackStandard;
        }
    }

    /**
     * Storage granted at a relay for one deposit (QO-30: effective = min(client request, relay storage)).
     *
     * @param relayStorageDays     storage of the relay
     * @param effectiveStorageDays storage granted to this parcel
     * @param expiresAt            end of storage
     * @param reminderAt           reminder time, {@code null} when no reminder
     */
    record StorageDeadline(int relayStorageDays, int effectiveStorageDays, Instant expiresAt, Instant reminderAt) {
    }

    /**
     * @param agencyId the {@code Delivery.agencyId} (agency registry id), {@code null} for a platform delivery
     * @return the thresholds of the agency's Kernel organisation, else the platform values
     */
    Mono<Thresholds> forAgency(UUID agencyId);

    /**
     * @param hubId                 relay hub of the deposit
     * @param depositedAt           deposit time
     * @param requestedStorageDays  storage asked by the client, may be {@code null}
     * @return the storage deadline (relay owner override, independent relay value, else platform)
     */
    Mono<StorageDeadline> relayStorageDeadline(UUID hubId, Instant depositedAt, Integer requestedStorageDays);

    /** Forgets every cached value (a threshold changed). */
    void invalidateAll();
}
