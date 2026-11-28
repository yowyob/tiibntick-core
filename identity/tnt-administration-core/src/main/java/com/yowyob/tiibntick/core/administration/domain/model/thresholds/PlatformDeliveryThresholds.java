package com.yowyob.tiibntick.core.administration.domain.model.thresholds;

import com.yowyob.tiibntick.common.exception.TntValidationException.FieldViolation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Platform-wide delivery thresholds, set by the TiiBnTick administrator — one value for the whole platform, never
 * per tenant ([B.5], D-087). Every threshold is set, and so are the QO-29 bounds of independent relays
 * (3..30 days by default, D-130).
 *
 * @param values               every {@link DeliveryThreshold}
 * @param independentRelayStorage QO-29 bounds for independent relays
 * @param updatedBy            user who made the last change ({@code null} for the seeded row)
 * @param updatedAt            time of the last change
 * @param version              optimistic-lock version
 */
public record PlatformDeliveryThresholds(
        DeliveryThresholdValues values,
        RelayStorageBounds independentRelayStorage,
        UUID updatedBy,
        Instant updatedAt,
        long version) {

    public PlatformDeliveryThresholds {
        Objects.requireNonNull(values, "values");
        independentRelayStorage = independentRelayStorage == null ? RelayStorageBounds.DEFAULT : independentRelayStorage;
    }

    /** The decided defaults, as seeded by changesets {@code evt-l1-001} and {@code evt-l1-003}. */
    public static PlatformDeliveryThresholds defaults() {
        return new PlatformDeliveryThresholds(DeliveryThresholdValues.defaults(), RelayStorageBounds.DEFAULT,
                null, Instant.EPOCH, 0L);
    }

    public int get(DeliveryThreshold threshold) {
        Integer value = values.get(threshold);
        if (value == null) {
            throw new IllegalStateException("Platform threshold " + threshold + " is not set");
        }
        return value;
    }

    /**
     * Checks a new platform setting: every threshold set and in range, reminder before the end of storage, relay
     * bounds within 1..60 days and min ≤ max.
     *
     * @throws com.yowyob.tiibntick.common.exception.TntValidationException {@code DELIVERY_THRESHOLDS_INVALID}
     */
    public PlatformDeliveryThresholds validate() {
        List<FieldViolation> violations = DeliveryThresholdsValidation.newList();
        for (DeliveryThreshold t : DeliveryThreshold.values()) {
            if (!values.isSet(t)) {
                violations.add(new FieldViolation(t.field(), "is required"));
            }
        }
        DeliveryThresholdsValidation.checkRanges(values, violations);
        Integer storage = values.get(DeliveryThreshold.RELAY_STORAGE_DAYS);
        Integer reminder = values.get(DeliveryThreshold.RELAY_STORAGE_REMINDER_DAYS_BEFORE);
        if (storage != null && reminder != null) {
            DeliveryThresholdsValidation.checkReminder(storage, reminder, violations);
        }
        checkBound("independentRelayStorageMinDays", independentRelayStorage.minDays(), violations);
        checkBound("independentRelayStorageMaxDays", independentRelayStorage.maxDays(), violations);
        if (independentRelayStorage.minDays() > independentRelayStorage.maxDays()) {
            violations.add(new FieldViolation("independentRelayStorageMinDays",
                    "must not exceed independentRelayStorageMaxDays", independentRelayStorage.minDays()));
        }
        DeliveryThresholdsValidation.throwIfAny(violations, "Invalid platform delivery thresholds");
        return this;
    }

    private static void checkBound(String field, int days, List<FieldViolation> violations) {
        DeliveryThreshold storage = DeliveryThreshold.RELAY_STORAGE_DAYS;
        if (!storage.accepts(days)) {
            violations.add(new FieldViolation(field,
                    "must be between " + storage.min() + " and " + storage.max(), days));
        }
    }
}
