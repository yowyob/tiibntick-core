package com.yowyob.tiibntick.core.administration.adapter.in.web.thresholds;

import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThreshold;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThresholdValues;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.EffectiveDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.OrganizationDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.PlatformDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.ResolvedThreshold;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.ThresholdSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThreshold.*;

/** Request and response bodies of the delivery-threshold routes (one JSON field per {@link DeliveryThreshold}). */
public final class DeliveryThresholdsDtos {

    private DeliveryThresholdsDtos() {
    }

    /** {@code PUT /api/v1/admin/settings/delivery-thresholds}: every threshold is required. */
    @Schema(name = "PlatformDeliveryThresholdsRequest")
    public record PlatformRequest(
            @NotNull Integer maxDeliveryAttempts,
            @NotNull Integer relayStorageDays,
            @NotNull Integer relayStorageReminderDaysBefore,
            @NotNull Integer maxFailedReturns,
            @NotNull Integer pickupLateGraceMinutes,
            @NotNull Integer deliveryLateGraceMinutes,
            @NotNull Integer deliveryLateFallbackHoursStandard,
            @NotNull Integer deliveryLateFallbackHoursExpress,
            @NotNull Integer deliveryLateFallbackHoursSameDay,
            @NotNull Integer etaExceededGraceMinutes,
            @NotNull Integer gpsSignalLostMinutes,
            @NotNull Integer prolongedStopMinutes,
            @Schema(description = "QO-29: lowest storage an independent relay may set (default 3, D-130)")
            @NotNull Integer independentRelayStorageMinDays,
            @Schema(description = "QO-29: highest storage an independent relay may set (default 30, D-130)")
            @NotNull Integer independentRelayStorageMaxDays,
            @Schema(description = "Version read by the caller (optimistic lock, 409 when stale)")
            @NotNull Long version) {

        DeliveryThresholdValues values() {
            return DeliveryThresholdsDtos.values(maxDeliveryAttempts, relayStorageDays, relayStorageReminderDaysBefore,
                    maxFailedReturns, pickupLateGraceMinutes, deliveryLateGraceMinutes,
                    deliveryLateFallbackHoursStandard, deliveryLateFallbackHoursExpress,
                    deliveryLateFallbackHoursSameDay, etaExceededGraceMinutes, gpsSignalLostMinutes,
                    prolongedStopMinutes);
        }
    }

    @Schema(name = "PlatformDeliveryThresholdsResponse")
    public record PlatformResponse(
            int maxDeliveryAttempts, int relayStorageDays, int relayStorageReminderDaysBefore, int maxFailedReturns,
            int pickupLateGraceMinutes, int deliveryLateGraceMinutes, int deliveryLateFallbackHoursStandard,
            int deliveryLateFallbackHoursExpress, int deliveryLateFallbackHoursSameDay, int etaExceededGraceMinutes,
            int gpsSignalLostMinutes, int prolongedStopMinutes,
            Integer independentRelayStorageMinDays, Integer independentRelayStorageMaxDays,
            Instant updatedAt, UUID updatedBy, long version) {

        static PlatformResponse from(PlatformDeliveryThresholds p) {
            return new PlatformResponse(p.get(MAX_DELIVERY_ATTEMPTS), p.get(RELAY_STORAGE_DAYS),
                    p.get(RELAY_STORAGE_REMINDER_DAYS_BEFORE), p.get(MAX_FAILED_RETURNS),
                    p.get(PICKUP_LATE_GRACE_MINUTES), p.get(DELIVERY_LATE_GRACE_MINUTES),
                    p.get(DELIVERY_LATE_FALLBACK_HOURS_STANDARD), p.get(DELIVERY_LATE_FALLBACK_HOURS_EXPRESS),
                    p.get(DELIVERY_LATE_FALLBACK_HOURS_SAME_DAY), p.get(ETA_EXCEEDED_GRACE_MINUTES),
                    p.get(GPS_SIGNAL_LOST_MINUTES), p.get(PROLONGED_STOP_MINUTES),
                    p.independentRelayStorage().minDays(), p.independentRelayStorage().maxDays(),
                    p.updatedAt(), p.updatedBy(), p.version());
        }
    }

    /**
     * {@code PUT /api/v1/organizations/{organizationId}/settings/delivery-thresholds}: only the overridden thresholds
     * (null = inherit the platform value); {@code version} null to create, else the version read.
     */
    @Schema(name = "OrganizationDeliveryThresholdsRequest")
    public record OrganizationRequest(
            Integer maxDeliveryAttempts, Integer relayStorageDays, Integer relayStorageReminderDaysBefore,
            Integer maxFailedReturns, Integer pickupLateGraceMinutes, Integer deliveryLateGraceMinutes,
            Integer deliveryLateFallbackHoursStandard, Integer deliveryLateFallbackHoursExpress,
            Integer deliveryLateFallbackHoursSameDay, Integer etaExceededGraceMinutes, Integer gpsSignalLostMinutes,
            Integer prolongedStopMinutes,
            @Schema(description = "null to create the override, else the version read (409 when stale)")
            Long version) {

        DeliveryThresholdValues values() {
            return DeliveryThresholdsDtos.values(maxDeliveryAttempts, relayStorageDays, relayStorageReminderDaysBefore,
                    maxFailedReturns, pickupLateGraceMinutes, deliveryLateGraceMinutes,
                    deliveryLateFallbackHoursStandard, deliveryLateFallbackHoursExpress,
                    deliveryLateFallbackHoursSameDay, etaExceededGraceMinutes, gpsSignalLostMinutes,
                    prolongedStopMinutes);
        }
    }

    /** An organization override; every field null and {@code version} null when the organization has none. */
    @Schema(name = "OrganizationDeliveryThresholdsResponse")
    public record OrganizationResponse(
            UUID organizationId,
            Integer maxDeliveryAttempts, Integer relayStorageDays, Integer relayStorageReminderDaysBefore,
            Integer maxFailedReturns, Integer pickupLateGraceMinutes, Integer deliveryLateGraceMinutes,
            Integer deliveryLateFallbackHoursStandard, Integer deliveryLateFallbackHoursExpress,
            Integer deliveryLateFallbackHoursSameDay, Integer etaExceededGraceMinutes, Integer gpsSignalLostMinutes,
            Integer prolongedStopMinutes,
            Instant updatedAt, UUID updatedBy, Long version) {

        static OrganizationResponse none(UUID organizationId) {
            return of(organizationId, DeliveryThresholdValues.empty(), null, null, null);
        }

        static OrganizationResponse from(OrganizationDeliveryThresholds o) {
            return of(o.organizationId(), o.values(), o.updatedAt(), o.updatedBy(), o.version());
        }

        private static OrganizationResponse of(UUID organizationId, DeliveryThresholdValues v, Instant updatedAt,
                                               UUID updatedBy, Long version) {
            return new OrganizationResponse(organizationId, v.get(MAX_DELIVERY_ATTEMPTS), v.get(RELAY_STORAGE_DAYS),
                    v.get(RELAY_STORAGE_REMINDER_DAYS_BEFORE), v.get(MAX_FAILED_RETURNS),
                    v.get(PICKUP_LATE_GRACE_MINUTES), v.get(DELIVERY_LATE_GRACE_MINUTES),
                    v.get(DELIVERY_LATE_FALLBACK_HOURS_STANDARD), v.get(DELIVERY_LATE_FALLBACK_HOURS_EXPRESS),
                    v.get(DELIVERY_LATE_FALLBACK_HOURS_SAME_DAY), v.get(ETA_EXCEEDED_GRACE_MINUTES),
                    v.get(GPS_SIGNAL_LOST_MINUTES), v.get(PROLONGED_STOP_MINUTES), updatedAt, updatedBy, version);
        }
    }

    /** One effective value and its source ({@code PLATFORM}, {@code ORGANIZATION}). */
    @Schema(name = "EffectiveThresholdValue")
    public record Value(int value, ThresholdSource source) {
        static Value of(ResolvedThreshold r) {
            return new Value(r.value(), r.source());
        }
    }

    /** {@code GET /api/v1/organizations/{organizationId}/settings/delivery-thresholds/effective}. */
    @Schema(name = "EffectiveDeliveryThresholdsResponse")
    public record EffectiveResponse(
            UUID organizationId, boolean agencyOrganization,
            Value maxDeliveryAttempts, Value relayStorageDays, Value relayStorageReminderDaysBefore,
            Value maxFailedReturns, Value pickupLateGraceMinutes, Value deliveryLateGraceMinutes,
            Value deliveryLateFallbackHoursStandard, Value deliveryLateFallbackHoursExpress,
            Value deliveryLateFallbackHoursSameDay, Value etaExceededGraceMinutes, Value gpsSignalLostMinutes,
            Value prolongedStopMinutes,
            Integer independentRelayStorageMinDays, Integer independentRelayStorageMaxDays) {

        static EffectiveResponse from(EffectiveDeliveryThresholds e) {
            return new EffectiveResponse(e.organizationId(), e.agencyOrganization(),
                    Value.of(e.get(MAX_DELIVERY_ATTEMPTS)), Value.of(e.get(RELAY_STORAGE_DAYS)),
                    Value.of(e.get(RELAY_STORAGE_REMINDER_DAYS_BEFORE)), Value.of(e.get(MAX_FAILED_RETURNS)),
                    Value.of(e.get(PICKUP_LATE_GRACE_MINUTES)), Value.of(e.get(DELIVERY_LATE_GRACE_MINUTES)),
                    Value.of(e.get(DELIVERY_LATE_FALLBACK_HOURS_STANDARD)),
                    Value.of(e.get(DELIVERY_LATE_FALLBACK_HOURS_EXPRESS)),
                    Value.of(e.get(DELIVERY_LATE_FALLBACK_HOURS_SAME_DAY)), Value.of(e.get(ETA_EXCEEDED_GRACE_MINUTES)),
                    Value.of(e.get(GPS_SIGNAL_LOST_MINUTES)), Value.of(e.get(PROLONGED_STOP_MINUTES)),
                    e.independentRelayStorage().minDays(), e.independentRelayStorage().maxDays());
        }
    }

    private static DeliveryThresholdValues values(Integer maxDeliveryAttempts, Integer relayStorageDays,
                                                  Integer relayStorageReminderDaysBefore, Integer maxFailedReturns,
                                                  Integer pickupLateGraceMinutes, Integer deliveryLateGraceMinutes,
                                                  Integer fallbackStandard, Integer fallbackExpress,
                                                  Integer fallbackSameDay, Integer etaExceededGraceMinutes,
                                                  Integer gpsSignalLostMinutes, Integer prolongedStopMinutes) {
        Map<DeliveryThreshold, Integer> map = new EnumMap<>(DeliveryThreshold.class);
        put(map, MAX_DELIVERY_ATTEMPTS, maxDeliveryAttempts);
        put(map, RELAY_STORAGE_DAYS, relayStorageDays);
        put(map, RELAY_STORAGE_REMINDER_DAYS_BEFORE, relayStorageReminderDaysBefore);
        put(map, MAX_FAILED_RETURNS, maxFailedReturns);
        put(map, PICKUP_LATE_GRACE_MINUTES, pickupLateGraceMinutes);
        put(map, DELIVERY_LATE_GRACE_MINUTES, deliveryLateGraceMinutes);
        put(map, DELIVERY_LATE_FALLBACK_HOURS_STANDARD, fallbackStandard);
        put(map, DELIVERY_LATE_FALLBACK_HOURS_EXPRESS, fallbackExpress);
        put(map, DELIVERY_LATE_FALLBACK_HOURS_SAME_DAY, fallbackSameDay);
        put(map, ETA_EXCEEDED_GRACE_MINUTES, etaExceededGraceMinutes);
        put(map, GPS_SIGNAL_LOST_MINUTES, gpsSignalLostMinutes);
        put(map, PROLONGED_STOP_MINUTES, prolongedStopMinutes);
        return DeliveryThresholdValues.of(map);
    }

    private static void put(Map<DeliveryThreshold, Integer> map, DeliveryThreshold key, Integer value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
