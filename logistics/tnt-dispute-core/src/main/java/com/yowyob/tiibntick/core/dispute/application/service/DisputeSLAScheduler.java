package com.yowyob.tiibntick.core.dispute.application.service;

import com.yowyob.tiibntick.core.dispute.application.command.CloseDisputeCommand;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeCommandUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeNotificationPort;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeRepository;
import com.yowyob.tiibntick.core.dispute.domain.enums.ClosureType;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Scheduled service that enforces SLA timelines across all active disputes.
 *
 * <p>Runs periodically to:
 * <ul>
 *   <li>Detect disputes whose global resolution deadline has passed → auto-close as EXPIRED</li>
 *   <li>Detect disputes in AWAITING_EVIDENCE that have timed out → auto-close as EXPIRED</li>
 *   <li>Emit SLA breach notifications to the dispute management team</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Component
public class DisputeSLAScheduler {

    private static final Logger log = LoggerFactory.getLogger(DisputeSLAScheduler.class);

    private final IDisputeRepository repository;
    private final IDisputeCommandUseCase commandUseCase;
    private final IDisputeNotificationPort notificationPort;

    public DisputeSLAScheduler(
            final IDisputeRepository repository,
            final IDisputeCommandUseCase commandUseCase,
            final IDisputeNotificationPort notificationPort) {
        this.repository = Objects.requireNonNull(repository);
        this.commandUseCase = Objects.requireNonNull(commandUseCase);
        this.notificationPort = Objects.requireNonNull(notificationPort);
    }

    /**
     * Runs every hour to detect and process globally expired disputes.
     * A dispute is expired when its resolution deadline has passed without any closure.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void processExpiredDisputes() {
        log.info("Running SLA expiry check at {}", LocalDateTime.now());
        final LocalDateTime now = LocalDateTime.now();

        repository.findExpiredByStatusBefore(DisputeStatus.OPEN, now)
                .mergeWith(repository.findExpiredByStatusBefore(DisputeStatus.UNDER_INVESTIGATION, now))
                .mergeWith(repository.findExpiredByStatusBefore(DisputeStatus.AWAITING_EVIDENCE, now))
                .mergeWith(repository.findExpiredByStatusBefore(DisputeStatus.MEDIATION_IN_PROGRESS, now))
                .flatMap(this::expireDispute)
                .doOnError(e -> log.error("SLA expiry check error: {}", e.getMessage()))
                .subscribe();
    }

    /**
     * Runs every 30 minutes to detect response SLA breaches (no mediator assigned).
     * Issues an alert but does not close the dispute automatically.
     */
    @Scheduled(cron = "0 0/30 * * * *")
    public void checkResponseSlaBreaches() {
        log.debug("Checking response SLA breaches at {}", LocalDateTime.now());
        final LocalDateTime now = LocalDateTime.now();

        repository.findExpiredByStatusBefore(DisputeStatus.OPEN, now)
                .flatMap(dispute -> {
                    dispute.markSlaBreached("Initial response SLA breached — no mediator assigned within deadline");
                    return notificationPort
                            .notifySlaBreached(dispute, "INITIAL_RESPONSE_SLA")
                            .onErrorResume(e -> {
                                log.warn("Failed to send SLA breach notification for dispute {}: {}", dispute.getId(), e.getMessage());
                                return Mono.empty();
                            });
                })
                .subscribe();
    }

    private Mono<Dispute> expireDispute(final Dispute dispute) {
        log.warn("Expiring dispute id={} ref={} status={}", dispute.getId(), dispute.getReference(), dispute.getStatus());

        final CloseDisputeCommand cmd = new CloseDisputeCommand(
                dispute.getId(),
                dispute.getTenantId(),
                "SYSTEM",
                ClosureType.EXPIRED,
                "Automatically closed: SLA resolution deadline exceeded");

        return commandUseCase.closeDispute(cmd)
                .doOnSuccess(d -> log.info("Dispute {} expired and closed successfully", d.getId()))
                .onErrorResume(e -> {
                    log.error("Failed to expire dispute {}: {}", dispute.getId(), e.getMessage());
                    return Mono.empty();
                });
    }
}
