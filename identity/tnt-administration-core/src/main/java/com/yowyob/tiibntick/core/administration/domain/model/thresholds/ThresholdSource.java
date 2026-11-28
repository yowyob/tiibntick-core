package com.yowyob.tiibntick.core.administration.domain.model.thresholds;

/** Where an effective threshold value comes from. */
public enum ThresholdSource {
    /** The platform value set by the TiiBnTick administrator. */
    PLATFORM,
    /** The override of the TiiBnTick Agency organization. */
    ORGANIZATION,
    /** The storage duration an independent relay set for itself, within the admin's bounds (QO-29). */
    RELAY
}
