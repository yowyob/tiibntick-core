package com.yowyob.tiibntick.core.delivery.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Running of the delivery watch job ({@code tnt.delivery.alerts.*}, lot L3). The alert thresholds themselves (QO-18)
 * are delivery thresholds of the administration module: platform values set by the TiiBnTick administrator, overridable
 * by an Agency organisation (D-131 to D-133), read through {@code DeliveryThresholdsPort}.
 */
@Getter
@Setter
@ConfigurationProperties("tnt.delivery.alerts")
public class DeliveryAlertProperties {

    /** Whether the watch job runs. */
    private boolean enabled = true;
    /** Cron of the watch job. */
    private String cron = "0 */5 * * * *";
    /** Deliveries evaluated per run. */
    private int batchSize = 500;
}
