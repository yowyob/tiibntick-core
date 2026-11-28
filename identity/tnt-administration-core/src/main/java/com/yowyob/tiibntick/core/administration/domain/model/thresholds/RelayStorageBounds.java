package com.yowyob.tiibntick.core.administration.domain.model.thresholds;

/**
 * QO-29: range in which an independent relay (not a TiiBnTick Agency organization) may set its own storage duration,
 * fixed by the TiiBnTick administrator. Always set: 3 to 30 days by default (decision D-130), adjustable by the
 * administrator within the input range of {@link DeliveryThreshold#RELAY_STORAGE_DAYS} (1..60 days, D-131).
 *
 * @param minDays lowest accepted duration in days
 * @param maxDays highest accepted duration in days
 */
public record RelayStorageBounds(int minDays, int maxDays) {

    /** D-130: default bounds, seeded by changeset {@code evt-l1-003}. */
    public static final RelayStorageBounds DEFAULT = new RelayStorageBounds(3, 30);

    public boolean contains(int days) {
        return days >= minDays && days <= maxDays;
    }

    /** The relay's own value brought back into the range (used when the admin narrowed it afterwards). */
    public int clamp(int days) {
        return Math.max(minDays, Math.min(maxDays, days));
    }
}
