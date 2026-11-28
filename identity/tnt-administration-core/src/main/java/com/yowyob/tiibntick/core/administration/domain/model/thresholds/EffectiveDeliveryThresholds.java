package com.yowyob.tiibntick.core.administration.domain.model.thresholds;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Thresholds as they apply to one organization (or to the platform when there is none), each with its source.
 *
 * <p>Resolution (spec §5.1, [B.5]): for each threshold separately, the organization's value when the organization is
 * a TiiBnTick Agency and the value is set, else the platform value. Use {@link #of(PlatformDeliveryThresholds,
 * UUID, boolean, OrganizationDeliveryThresholds)}; the relay storage of an independent relay is resolved by
 * {@link #relayStorageDays(Integer)} (QO-29).
 *
 * @param organizationId          the organization asked for, or {@code null}
 * @param agencyOrganization      whether that organization is a TiiBnTick Agency (only agencies override)
 * @param thresholds              every threshold, resolved
 * @param independentRelayStorage QO-29 bounds (platform only)
 */
public record EffectiveDeliveryThresholds(
        UUID organizationId,
        boolean agencyOrganization,
        Map<DeliveryThreshold, ResolvedThreshold> thresholds,
        RelayStorageBounds independentRelayStorage) {

    public EffectiveDeliveryThresholds {
        thresholds = Collections.unmodifiableMap(new EnumMap<>(Objects.requireNonNull(thresholds, "thresholds")));
        independentRelayStorage = independentRelayStorage == null ? RelayStorageBounds.DEFAULT : independentRelayStorage;
    }

    /**
     * Resolves the thresholds of an organization.
     *
     * @param platform           platform thresholds
     * @param organizationId     organization, or {@code null} (platform values)
     * @param agencyOrganization whether the organization is a TiiBnTick Agency; otherwise its override is ignored
     * @param override           its override, or {@code null}
     */
    public static EffectiveDeliveryThresholds of(PlatformDeliveryThresholds platform, UUID organizationId,
                                                 boolean agencyOrganization, OrganizationDeliveryThresholds override) {
        Objects.requireNonNull(platform, "platform");
        boolean agency = organizationId != null && agencyOrganization;
        DeliveryThresholdValues own = agency && override != null ? override.values() : DeliveryThresholdValues.empty();
        Map<DeliveryThreshold, ResolvedThreshold> resolved = new EnumMap<>(DeliveryThreshold.class);
        for (DeliveryThreshold t : DeliveryThreshold.values()) {
            Integer value = own.get(t);
            resolved.put(t, value != null
                    ? new ResolvedThreshold(value, ThresholdSource.ORGANIZATION)
                    : new ResolvedThreshold(platform.get(t), ThresholdSource.PLATFORM));
        }
        return new EffectiveDeliveryThresholds(organizationId, agency, resolved, platform.independentRelayStorage());
    }

    public ResolvedThreshold get(DeliveryThreshold threshold) {
        return thresholds.get(threshold);
    }

    public int value(DeliveryThreshold threshold) {
        return get(threshold).value();
    }

    public int maxDeliveryAttempts() { return value(DeliveryThreshold.MAX_DELIVERY_ATTEMPTS); }

    public int relayStorageReminderDaysBefore() { return value(DeliveryThreshold.RELAY_STORAGE_REMINDER_DAYS_BEFORE); }

    public int maxFailedReturns() { return value(DeliveryThreshold.MAX_FAILED_RETURNS); }

    /**
     * Storage duration at a relay owned by this organization.
     *
     * <ul>
     *   <li>agency relay: the agency's override, else the platform value — the relay's own value is ignored;</li>
     *   <li>independent relay with its own value (QO-29): that value, brought within the admin's bounds;</li>
     *   <li>otherwise the platform value.</li>
     * </ul>
     *
     * @param independentRelayStorageDays storage duration the independent relay set for itself, or {@code null}
     */
    public ResolvedThreshold relayStorageDays(Integer independentRelayStorageDays) {
        ResolvedThreshold resolved = get(DeliveryThreshold.RELAY_STORAGE_DAYS);
        if (agencyOrganization || independentRelayStorageDays == null) {
            return resolved;
        }
        return new ResolvedThreshold(independentRelayStorage.clamp(independentRelayStorageDays), ThresholdSource.RELAY);
    }
}
