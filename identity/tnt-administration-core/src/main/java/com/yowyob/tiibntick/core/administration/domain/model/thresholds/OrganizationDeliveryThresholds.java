package com.yowyob.tiibntick.core.administration.domain.model.thresholds;

import com.yowyob.tiibntick.common.exception.TntValidationException.FieldViolation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Overrides of the platform thresholds for one TiiBnTick Agency organization ([B.5], D-087, QO-18). A threshold that
 * is not set inherits the platform value. Who, inside the organization, may change it is decided by the agency's
 * manager by granting the {@code organization:delivery-settings:write} permission.
 *
 * @param organizationId Kernel organization id of the agency (unique)
 * @param values         the overridden thresholds only
 * @param updatedBy      user who made the last change
 * @param updatedAt      time of the last change
 * @param version        optimistic-lock version
 */
public record OrganizationDeliveryThresholds(
        UUID organizationId,
        DeliveryThresholdValues values,
        UUID updatedBy,
        Instant updatedAt,
        long version) {

    public OrganizationDeliveryThresholds {
        Objects.requireNonNull(organizationId, "organizationId");
        values = values == null ? DeliveryThresholdValues.empty() : values;
    }

    /**
     * Checks the overrides against the current platform values: every set value in range and the reminder strictly
     * before the end of storage, each resolved as the agency will see it (override, else platform).
     *
     * @throws com.yowyob.tiibntick.common.exception.TntValidationException {@code DELIVERY_THRESHOLDS_INVALID}
     */
    public OrganizationDeliveryThresholds validateAgainst(PlatformDeliveryThresholds platform) {
        List<FieldViolation> violations = DeliveryThresholdsValidation.newList();
        DeliveryThresholdsValidation.checkRanges(values, violations);
        if (values.isSet(DeliveryThreshold.RELAY_STORAGE_DAYS)
                || values.isSet(DeliveryThreshold.RELAY_STORAGE_REMINDER_DAYS_BEFORE)) {
            DeliveryThresholdsValidation.checkReminder(
                    effective(DeliveryThreshold.RELAY_STORAGE_DAYS, platform),
                    effective(DeliveryThreshold.RELAY_STORAGE_REMINDER_DAYS_BEFORE, platform), violations);
        }
        DeliveryThresholdsValidation.throwIfAny(violations, "Invalid delivery thresholds for organization "
                + organizationId);
        return this;
    }

    private int effective(DeliveryThreshold threshold, PlatformDeliveryThresholds platform) {
        Integer own = values.get(threshold);
        return own != null ? own : platform.get(threshold);
    }
}
