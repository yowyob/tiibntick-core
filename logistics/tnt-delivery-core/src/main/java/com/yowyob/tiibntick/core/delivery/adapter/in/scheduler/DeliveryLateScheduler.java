package com.yowyob.tiibntick.core.delivery.adapter.in.scheduler;

import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.core.delivery.application.service.watch.DeliveryWatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Time-based delivery alerts (lot L3, QO-18): {@code PICKUP_LATE}, {@code DELIVERY_LATE}, {@code ETA_EXCEEDED},
 * {@code GPS_SIGNAL_LOST}. Cron {@code tnt.delivery.alerts.cron} (every 5 min); one instance at a time (ShedLock).
 * The run is awaited (bounded), so that a failure is logged by the job and the lock is held for the real duration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tnt.delivery.alerts", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DeliveryLateScheduler {

    static final Duration MAX_RUN = Duration.ofMinutes(4);

    private final DeliveryWatchService watchService;

    @Scheduled(cron = "${tnt.delivery.alerts.cron:0 */5 * * * *}")
    @SchedulerLock(name = "delivery-alert-watch", lockAtMostFor = "PT4M", lockAtLeastFor = "PT30S")
    public void run() {
        try {
            Long raised = ReactiveListenerSupport.awaitBounded(watchService.scan(Instant.now()), MAX_RUN);
            if (raised != null && raised > 0) {
                log.info("Delivery alert watch: {} delivery(ies) with a new alert", raised);
            }
        } catch (RuntimeException e) {
            log.error("Delivery alert watch failed", e);
        }
    }
}
