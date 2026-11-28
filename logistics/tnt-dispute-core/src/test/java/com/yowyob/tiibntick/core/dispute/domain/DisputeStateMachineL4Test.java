package com.yowyob.tiibntick.core.dispute.domain;

import com.yowyob.tiibntick.core.dispute.application.command.EscalateDisputeCommand;
import com.yowyob.tiibntick.core.dispute.domain.enums.*;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeAccessDeniedException;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeStateException;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeStateMachine;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeTransition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus.*;
import static com.yowyob.tiibntick.core.dispute.support.DisputeFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lot L4: the dispute follows the table of spec §1.4 (LT1–LT12) and stacks one event per change. */
class DisputeStateMachineL4Test {

    @Test
    @DisplayName("the table holds exactly the rows LT2–LT12 of the spec")
    void tableIsTheSpec() {
        assertThat(DisputeStateMachine.targets(OPEN))
                .containsExactlyInAnyOrder(UNDER_INVESTIGATION, CLOSED_WITHDRAWN, CLOSED_EXPIRED, CLOSED_RESOLVED);
        assertThat(DisputeStateMachine.targets(UNDER_INVESTIGATION)).containsExactlyInAnyOrder(AWAITING_EVIDENCE,
                MEDIATION_IN_PROGRESS, PENDING_ARBITRATION, CLOSED_WITHDRAWN, CLOSED_EXPIRED, CLOSED_RESOLVED);
        assertThat(DisputeStateMachine.targets(AWAITING_EVIDENCE)).containsExactlyInAnyOrder(UNDER_INVESTIGATION,
                CLOSED_WITHDRAWN, CLOSED_EXPIRED, CLOSED_RESOLVED);
        assertThat(DisputeStateMachine.targets(MEDIATION_IN_PROGRESS)).containsExactlyInAnyOrder(PENDING_ARBITRATION,
                PENDING_COMPENSATION, CLOSED_RESOLVED, CLOSED_WITHDRAWN);
        assertThat(DisputeStateMachine.targets(PENDING_ARBITRATION)).containsExactlyInAnyOrder(PENDING_COMPENSATION,
                CLOSED_RESOLVED, CLOSED_WITHDRAWN);
        assertThat(DisputeStateMachine.targets(PENDING_COMPENSATION)).containsExactly(COMPENSATED);
        for (DisputeStatus terminal : EnumSet.of(COMPENSATED, CLOSED_RESOLVED, CLOSED_WITHDRAWN, CLOSED_EXPIRED)) {
            assertThat(DisputeStateMachine.targets(terminal)).as(terminal.name()).isEmpty();
        }
    }

    @Test
    @DisplayName("a full lifecycle LT1→LT2→LT3→LT4→LT5→LT6→LT7→LT9 stacks ordered events with details")
    void fullLifecycle() {
        Dispute d = opened();
        d.assignMediator(MEDIATOR);
        d.requestAdditionalEvidence(CLAIMANT, inTwoDays());
        d.addEvidence(evidence(d, CLAIMANT));
        d.addEvidence(evidence(d, CLAIMANT));
        d.startMediation();
        d.escalate(new EscalateDisputeCommand(d.getId(), TENANT, MEDIATOR, "désaccord", null));
        d.rule(rule(d, ResolutionType.TOTAL_LOSS_COMPENSATED, new BigDecimal("15000")));
        d.processCompensation("KRN-PAY-1");

        List<DisputeTransition> t = d.getPendingTransitions();
        assertThat(t).extracting(DisputeTransition::eventKind).containsExactly("OPEN", "UNDER_INVESTIGATION",
                "AWAITING_EVIDENCE", "UNDER_INVESTIGATION", "EVIDENCE_ADDED", "MEDIATION_IN_PROGRESS",
                "PENDING_ARBITRATION", "PENDING_COMPENSATION", "COMPENSATED");
        assertThat(d.getEventSequence()).isEqualTo(t.size());
        assertThat(t.get(3).trigger()).isEqualTo("EVIDENCE_SUBMITTED");
        assertThat(t.get(7).details()).containsEntry("amount", "15000").containsEntry("currency", "XAF");
        assertThat(t.get(8).details()).containsEntry("paymentRef", "KRN-PAY-1");
        assertThat(d.getResponsibleParty()).isEqualTo(DisputeResponsibleParty.COURIER);
        assertThat(d.getClosedAt()).isNotNull();
        d.clearPendingTransitions();
        assertThat(d.getPendingTransitions()).isEmpty();
    }

    @Test
    @DisplayName("LT7 needs a compensating type with an amount > 0, LT8 a type without compensation")
    void rulingGuards() {
        Dispute a = inMediation();
        assertThatThrownBy(() -> a.rule(rule(a, ResolutionType.DAMAGE_COMPENSATED, null)))
                .isInstanceOf(DisputeStateException.class);
        assertThatThrownBy(() -> a.rule(rule(a, ResolutionType.COMPENSATION_GRANTED, BigDecimal.ZERO)))
                .isInstanceOf(DisputeStateException.class);
        assertThatThrownBy(() -> a.rule(rule(a, ResolutionType.COMPLAINT_DISMISSED, new BigDecimal("10"))))
                .isInstanceOf(DisputeStateException.class);
        assertThat(a.getStatus()).isEqualTo(MEDIATION_IN_PROGRESS);

        Dispute b = inMediation();
        b.rule(rule(b, ResolutionType.RETURN_TO_SENDER_ORDERED, null));
        assertThat(b.getStatus()).isEqualTo(CLOSED_RESOLVED);
        assertThat(b.getPendingTransitions().getLast().details())
                .containsEntry("resolutionType", "RETURN_TO_SENDER_ORDERED").containsEntry("closureType", null);
    }

    @ParameterizedTest
    @EnumSource(value = ClosureType.class, names = "ADMINISTRATIVE_CLOSURE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("C10: close() refuses every closure type but ADMINISTRATIVE_CLOSURE")
    void closeIsAdministrativeOnly(ClosureType type) {
        Dispute d = opened();
        assertThatThrownBy(() -> d.close(close(d, type, "motif"))).isInstanceOf(DisputeStateException.class);
        assertThat(d.getStatus()).isEqualTo(OPEN);
    }

    @Test
    @DisplayName("LT12: reason mandatory, never from PENDING_COMPENSATION; trigger ADMINISTRATIVE_CLOSURE")
    void administrativeClosure() {
        Dispute noReason = opened();
        assertThatThrownBy(() -> noReason.close(close(noReason, ClosureType.ADMINISTRATIVE_CLOSURE, " ")))
                .isInstanceOf(DisputeStateException.class);

        Dispute pending = inMediation();
        pending.rule(rule(pending, ResolutionType.COMPENSATION_GRANTED, new BigDecimal("500")));
        assertThatThrownBy(() -> pending.close(close(pending, ClosureType.ADMINISTRATIVE_CLOSURE, "doublon")))
                .isInstanceOf(DisputeStateException.class);
        assertThat(pending.getStatus()).isEqualTo(PENDING_COMPENSATION);

        Dispute d = opened();
        d.close(close(d, ClosureType.ADMINISTRATIVE_CLOSURE, "doublon"));
        assertThat(d.getStatus()).isEqualTo(CLOSED_RESOLVED);
        DisputeTransition last = d.getPendingTransitions().getLast();
        assertThat(last.trigger()).isEqualTo("ADMINISTRATIVE_CLOSURE");
        assertThat(last.details()).containsEntry("closureType", "ADMINISTRATIVE_CLOSURE");
    }

    @Test
    @DisplayName("LT10 only by the claimant; LT11 only from OPEN, UNDER_INVESTIGATION, AWAITING_EVIDENCE")
    void withdrawalAndExpiry() {
        Dispute d = opened();
        assertThatThrownBy(() -> d.withdraw(MEDIATOR)).isInstanceOf(DisputeAccessDeniedException.class);
        d.withdraw(CLAIMANT);
        assertThat(d.getStatus()).isEqualTo(CLOSED_WITHDRAWN);

        Dispute e = opened();
        e.expire();
        assertThat(e.getStatus()).isEqualTo(CLOSED_EXPIRED);
        assertThat(e.getPendingTransitions().getLast().actor().type().name()).isEqualTo("SYSTEM");

        Dispute m = inMediation();
        assertThatThrownBy(m::expire).isInstanceOf(DisputeStateException.class);
    }

    @Test
    @DisplayName("[B.7]: each resolution type decides the parcel outcome; 4 types grant a compensation")
    void parcelOutcomes() {
        Map<ResolutionType, String> expected = Map.of(
                ResolutionType.COMPENSATION_GRANTED, "RESTORE_PREVIOUS",
                ResolutionType.PARTIAL_COMPENSATION, "RESTORE_PREVIOUS",
                ResolutionType.TOTAL_LOSS_COMPENSATED, "LOST",
                ResolutionType.DAMAGE_COMPENSATED, "DAMAGED",
                ResolutionType.COMPLAINT_DISMISSED, "RESTORE_PREVIOUS",
                ResolutionType.MUTUAL_AGREEMENT, "RESTORE_PREVIOUS",
                ResolutionType.REDELIVERY_ORDERED, "REDELIVERY",
                ResolutionType.RETURN_TO_SENDER_ORDERED, "RETURN_TO_SENDER");
        assertThat(ResolutionType.values()).hasSize(8);
        expected.forEach((type, outcome) -> assertThat(type.parcelOutcome()).as(type.name()).isEqualTo(outcome));
        assertThat(Set.of(ResolutionType.values()).stream().filter(ResolutionType::grantsCompensation))
                .containsExactlyInAnyOrder(ResolutionType.COMPENSATION_GRANTED, ResolutionType.PARTIAL_COMPENSATION,
                        ResolutionType.TOTAL_LOSS_COMPENSATED, ResolutionType.DAMAGE_COMPENSATED);
    }

    @Test
    @DisplayName("an incident escalation opens with trigger INCIDENT_ESCALATED_TO_DISPUTE and openedFromIncidentId")
    void openedFromIncident() {
        var c = open(DisputeCause.PACKAGE_LOST, null, null);
        String incident = java.util.UUID.randomUUID().toString();
        var cmd = new com.yowyob.tiibntick.core.dispute.application.command.OpenDisputeCommand(c.tenantId(), "SYSTEM",
                ClaimantType.SYSTEM, c.respondentId(), c.respondentType(), c.cause(), c.category(), c.priority(),
                null, null, null, c.description(), null, null, false, incident);
        Dispute d = Dispute.open(cmd, com.yowyob.tiibntick.core.dispute.domain.model.DisputeReference.forSequence(1));
        DisputeTransition first = d.getPendingTransitions().getFirst();
        assertThat(first.trigger()).isEqualTo("INCIDENT_ESCALATED_TO_DISPUTE");
        assertThat(first.details()).containsEntry("openedFromIncidentId", incident);
        assertThat(first.actor().type().name()).isEqualTo("SYSTEM");
        assertThat(d.getIncidentId()).isEqualTo(incident);
    }
}
