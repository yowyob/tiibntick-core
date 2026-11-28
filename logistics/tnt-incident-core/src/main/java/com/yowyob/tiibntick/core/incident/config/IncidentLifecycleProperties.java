package com.yowyob.tiibntick.core.incident.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Incident lifecycle settings (lot L3), set by the administrator (admin spec §5.1): automatic confirmation delay of a
 * proposed resolution (QO-14), hand-over timeout (I15), hub transfer range (QO-13).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "tnt.incident.lifecycle")
public class IncidentLifecycleProperties {

    /** Runs the lifecycle job (automatic confirmation, hand-over timeout). */
    private boolean enabled = true;

    /** Lifecycle job schedule (Spring cron, server zone). */
    private String cron = "0 */5 * * * *";

    /** Incidents handled per run and per job. */
    private int batchSize = 200;

    /** QO-14: a proposed resolution not confirmed nor contested by the courier is confirmed after this delay. */
    private Duration confirmationDelay = Duration.ofHours(24);

    /** I15: a hand-over not confirmed by both couriers after this delay is aborted (replacement searched again). */
    private Duration handoverTimeout = Duration.ofHours(2);

    /** QO-13: without replacement courier, the parcels go to the nearest relay hub when it is within this range. */
    private double hubTransferMaxKm = 5.0;
}
