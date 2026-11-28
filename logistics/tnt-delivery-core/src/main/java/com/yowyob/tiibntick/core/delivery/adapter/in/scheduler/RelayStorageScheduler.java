package com.yowyob.tiibntick.core.delivery.adapter.in.scheduler;

import com.yowyob.tiibntick.core.delivery.application.service.RelayStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Hourly relay storage watch (spec §5.2, cron {@code tnt.delivery.relay-storage.cron}): reminders first, then the
 * expired storages (row D12). One instance at a time (ShedLock).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayStorageScheduler {

    private final RelayStorageService relayStorageService;

    @Scheduled(cron = "${tnt.delivery.relay-storage.cron:0 0 * * * *}")
    @SchedulerLock(name = "delivery-relay-storage", lockAtMostFor = "PT50M", lockAtLeastFor = "PT1M")
    public void run() {
        Instant now = Instant.now();
        relayStorageService.sendDueReminders(now)
                .zipWhen(reminders -> relayStorageService.expireStorage(now))
                .subscribe(
                        t -> {
                            if (t.getT1() > 0 || t.getT2() > 0) {
                                log.info("Relay storage watch: {} reminder(s), {} expiry(ies)", t.getT1(), t.getT2());
                            }
                        },
                        e -> log.error("Relay storage watch failed", e));
    }
}
