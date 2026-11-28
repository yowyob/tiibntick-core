package com.yowyob.tiibntick.core.administration.domain.model.thresholds;

import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.common.exception.TntValidationException.FieldViolation;

import java.util.ArrayList;
import java.util.List;

/** Validation rules shared by the platform thresholds and the organization overrides. */
final class DeliveryThresholdsValidation {

    static final String ERROR_CODE = "DELIVERY_THRESHOLDS_INVALID";

    private DeliveryThresholdsValidation() {
    }

    /** Range of every value that is set. */
    static void checkRanges(DeliveryThresholdValues values, List<FieldViolation> violations) {
        values.asMap().forEach((threshold, value) -> {
            if (!threshold.accepts(value)) {
                violations.add(new FieldViolation(threshold.field(),
                        "must be between " + threshold.min() + " and " + threshold.max(), value));
            }
        });
    }

    /** The reminder must come strictly before the end of storage (spec §5.1). */
    static void checkReminder(int storageDays, int reminderDaysBefore, List<FieldViolation> violations) {
        if (reminderDaysBefore >= storageDays) {
            violations.add(new FieldViolation(DeliveryThreshold.RELAY_STORAGE_REMINDER_DAYS_BEFORE.field(),
                    "must be lower than relayStorageDays (" + storageDays + ")", reminderDaysBefore));
        }
    }

    static void throwIfAny(List<FieldViolation> violations, String message) {
        if (!violations.isEmpty()) {
            throw new TntValidationException(ERROR_CODE, message, violations);
        }
    }

    static List<FieldViolation> newList() {
        return new ArrayList<>();
    }
}
