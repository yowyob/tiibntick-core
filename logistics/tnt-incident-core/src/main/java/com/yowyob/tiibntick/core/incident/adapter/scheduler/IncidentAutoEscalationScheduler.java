package com.yowyob.tiibntick.core.incident.adapter.scheduler;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentStatus;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentSeverity;
import com.yowyob.tiibntick.core.incident.port.inbound.IEscalateIncidentUseCase;
import com.yowyob.tiibntick.core.incident.port.outbound.IIncidentRepository;
import com.yowyob.tiibntick.core.incident.application.command.EscalateIncidentCommand;
import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
/**
 * Scheduled task (every 10 minutes) escalating incidents stagnant for 30+ minutes without resolution.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentAutoEscalationScheduler {
    private final IIncidentRepository incidentRepository;
    private final IEscalateIncidentUseCase escalateUseCase;
    private static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * Scheduled every 10 minutes. Finds incidents that have been stuck in
     * AUTO_RESOLUTION_FAILED or PENDING_AGENCY_ASSIGNMENT for over 30 minutes
     * and severity >= MEDIUM, then escalates them to the platform administrator.
     */
    @Scheduled(fixedDelayString = "${tnt.incident.auto-escalation.delay-ms:600000}")
    @SchedulerLock(name = "incident-auto-escalate-stagnant", lockAtMostFor = "PT9M", lockAtLeastFor = "PT1M")
    public void autoEscalateStagnant() {
        LockAssert.assertLocked();
        log.debug("Auto-escalation scheduler running at {}", Instant.now());
        Instant threshold = Instant.now().minus(30, ChronoUnit.MINUTES);
        incidentRepository.findByStatusIn(
                List.of(IncidentStatus.AUTO_RESOLUTION_FAILED,
                        IncidentStatus.PENDING_AGENCY_ASSIGNMENT),
                null
        ).filter(inc -> inc.getDetectedAt() != null && inc.getDetectedAt().isBefore(threshold)
                && inc.getSeverity().isAtLeast(IncidentSeverity.MEDIUM))
        .flatMap(incident -> escalateUseCase.execute(EscalateIncidentCommand.builder()
                .incidentId(incident.getId())
                .escalatedByActorId(SYSTEM_ACTOR)
                .escalatedByRole(ActorRole.SYSTEM)
                .targetRole(ActorRole.ADMIN_TIIBNTICK)
                .reason("Auto-escalation: incident stagnant for 30+ minutes without resolution")
                .triggerDispute(false)
                .build()))
        .subscribe(
                inc -> log.warn("Auto-escalated stagnant incident {}", inc.getReferenceCode()),
                err -> log.error("Auto-escalation error: {}", err.getMessage())
        );
    }
}
