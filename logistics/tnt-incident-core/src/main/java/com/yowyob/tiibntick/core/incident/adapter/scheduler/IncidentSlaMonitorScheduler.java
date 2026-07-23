package com.yowyob.tiibntick.core.incident.adapter.scheduler;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentStatus;
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
import java.util.List;
import java.util.UUID;
/**
 * Scheduled task (every 5 minutes) that auto-escalates incidents where the SLA has been breached.
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
public class IncidentSlaMonitorScheduler {
    private final IIncidentRepository incidentRepository;
    private final IEscalateIncidentUseCase escalateUseCase;
    private static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * Scheduled every 5 minutes. Finds all active incidents with a breached SLA
     * that have not yet been escalated and auto-escalates them to the agency manager.
     */
    @Scheduled(fixedDelayString = "${tnt.incident.sla-monitor.delay-ms:300000}")
    @SchedulerLock(name = "incident-sla-monitor", lockAtMostFor = "PT4M", lockAtLeastFor = "PT30S")
    public void monitorSlaBreaches() {
        LockAssert.assertLocked();
        log.debug("SLA breach monitor running at {}", Instant.now());
        incidentRepository.findByStatusIn(
                List.of(IncidentStatus.AUTO_RESOLVING, IncidentStatus.REASSIGNING_DRIVER,
                        IncidentStatus.AWAITING_HANDOVER, IncidentStatus.AGENCY_HANDLING),
                null
        ).filter(incident -> incident.getSlaImpact() != null && incident.getSlaImpact().isSlaBreached()
                && incident.getLastEscalationLevel() == 0)
        .flatMap(incident -> escalateUseCase.execute(EscalateIncidentCommand.builder()
                .incidentId(incident.getId())
                .escalatedByActorId(SYSTEM_ACTOR)
                .escalatedByRole(ActorRole.SYSTEM)
                .targetRole(ActorRole.AGENCY_MANAGER)
                .reason("SLA auto-escalation: breach of " + incident.getSlaImpact().getBreachMinutes() + " min")
                .triggerDispute(false)
                .build()))
        .subscribe(
                inc -> log.info("Auto-escalated SLA breach for incident {}", inc.getReferenceCode()),
                err -> log.error("SLA monitor error: {}", err.getMessage())
        );
    }
}
