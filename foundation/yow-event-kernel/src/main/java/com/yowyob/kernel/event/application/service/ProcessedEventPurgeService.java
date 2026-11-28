package com.yowyob.kernel.event.application.service;

import com.yowyob.kernel.event.application.port.out.ProcessedEventStorePort;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Scheduled purge of the consumer-side processed-event records older than the configured
 * retention ({@code yow.event.consumer-idempotency.retention}, 14 days by default). A duplicate
 * delivered after the retention is no longer caught — the retention must stay well above the
 * longest plausible redelivery delay (relay backoff + broker retention of unconsumed offsets).
 */
public class ProcessedEventPurgeService {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventPurgeService.class);

    private final ProcessedEventStorePort store;
    private final Duration retention;

    public ProcessedEventPurgeService(final ProcessedEventStorePort store, final Duration retention) {
        this.store = Objects.requireNonNull(store);
        this.retention = Objects.requireNonNull(retention);
    }

    @Scheduled(cron = "${yow.event.consumer-idempotency.purge-cron:0 40 3 * * *}")
    @SchedulerLock(name = "yow-event-processed-purge", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void scheduledPurge() {
        try {
            Long deleted = purge().block(Duration.ofMinutes(25));
            log.info("Processed-event purge: {} record(s) older than {} deleted", deleted, retention);
        } catch (RuntimeException e) {
            log.error("Processed-event purge failed: {}", e.toString(), e);
        }
    }

    public Mono<Long> purge() {
        return store.purgeOlderThan(retention);
    }
}
