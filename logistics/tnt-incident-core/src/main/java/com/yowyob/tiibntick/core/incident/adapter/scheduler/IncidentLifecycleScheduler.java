package com.yowyob.tiibntick.core.incident.adapter.scheduler;

import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.core.incident.application.service.IncidentHandoverService;
import com.yowyob.tiibntick.core.incident.port.inbound.IResolutionConfirmationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Incident lifecycle job (lot L3): confirms the resolutions the courier left unanswered past the delay (I24, QO-14)
 * and aborts the hand-overs not confirmed in time (I15 → D19). Settings: {@code tnt.incident.lifecycle.*}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tnt.incident.lifecycle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IncidentLifecycleScheduler {

    static final Duration MAX_RUN = Duration.ofMinutes(2);

    private final IResolutionConfirmationUseCase confirmation;
    private final IncidentHandoverService handover;

    @Scheduled(cron = "${tnt.incident.lifecycle.cron:0 */5 * * * *}")
    @SchedulerLock(name = "incident-lifecycle", lockAtMostFor = "PT4M", lockAtLeastFor = "PT30S")
    public void run() {
        Instant now = Instant.now();
        try {
            Long confirmed = ReactiveListenerSupport.awaitBounded(confirmation.confirmExpired(now), MAX_RUN);
            if (confirmed != null && confirmed > 0) {
                log.info("Incident lifecycle: {} resolution(s) confirmed automatically", confirmed);
            }
        } catch (RuntimeException e) {
            log.error("Incident lifecycle: automatic confirmation failed", e);
        }
        try {
            Long aborted = ReactiveListenerSupport.awaitBounded(handover.abortExpiredHandovers(now), MAX_RUN);
            if (aborted != null && aborted > 0) {
                log.info("Incident lifecycle: {} hand-over(s) aborted after the timeout", aborted);
            }
        } catch (RuntimeException e) {
            log.error("Incident lifecycle: hand-over timeout failed", e);
        }
    }
}
