package com.yowyob.tiibntick.core.administration.domain.model.thresholds;

import java.util.Objects;

/** An effective threshold value and its source. */
public record ResolvedThreshold(int value, ThresholdSource source) {

    public ResolvedThreshold {
        Objects.requireNonNull(source, "source");
    }
}
