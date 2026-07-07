package com.yowyob.tiibntick.core.incident.domain.model;

import com.yowyob.tiibntick.core.incident.domain.enums.AutoDecisionType;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Decision emitted by the automation engine, optionally requiring human confirmation before execution.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IncidentAutomationDecision {

    private UUID id;
    private UUID incidentId;
    private AutoDecisionType decisionType;
    private Instant decidedAt;
    private String decidedBySystem;
    private String parameters;
    private boolean executed;
    private Instant executedAt;
    private String executionResult;
    private String failureReason;
    private boolean requiresConfirmation;
    private UUID confirmedByActorId;
    private Instant confirmedAt;

    public static IncidentAutomationDecision decide(UUID incidentId, AutoDecisionType type,
                                                     String system, String parameters,
                                                     boolean requiresConfirmation) {
        return IncidentAutomationDecision.builder()
                .id(UUID.randomUUID())
                .incidentId(incidentId)
                .decisionType(type)
                .decidedAt(Instant.now())
                .decidedBySystem(system)
                .parameters(parameters)
                .requiresConfirmation(requiresConfirmation)
                .executed(false)
                .build();
    }

    public IncidentAutomationDecision markExecuted(String result) {
        return toBuilder().executed(true).executedAt(Instant.now()).executionResult(result).build();
    }

    public IncidentAutomationDecision markFailed(String reason) {
        return toBuilder().executed(false).failureReason(reason).build();
    }

    public IncidentAutomationDecision confirm(UUID actorId) {
        return toBuilder().confirmedByActorId(actorId).confirmedAt(Instant.now()).build();
    }

    public boolean canExecute() {
        return !executed && (!requiresConfirmation || confirmedByActorId != null);
    }

    public IncidentAutomationDecisionBuilder toBuilder() {
        return IncidentAutomationDecision.builder().id(id).incidentId(incidentId)
                .decisionType(decisionType).decidedAt(decidedAt).decidedBySystem(decidedBySystem)
                .parameters(parameters).executed(executed).executedAt(executedAt)
                .executionResult(executionResult).failureReason(failureReason)
                .requiresConfirmation(requiresConfirmation).confirmedByActorId(confirmedByActorId)
                .confirmedAt(confirmedAt);
    }
}
