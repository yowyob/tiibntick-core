package com.yowyob.tiibntick.core.incident.domain;

import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.incident.domain.enums.*;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentTransition;
import com.yowyob.tiibntick.core.incident.domain.service.IncidentBlockingPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.yowyob.tiibntick.core.incident.domain.enums.IncidentStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L3: the incident state machine of spec §1.3 (rows I1–I31), declarable types (QO-12), blocking rule (QO-2),
 * resolution confirmation (QO-14), transfer to dispute (QO-15), events recorded for {@code tnt.incident.events}.
 */
class IncidentLifecycleL3Test {

    private static final UUID COURIER = UUID.randomUUID();

    /** The table of spec §1.3, written out row by row (independently of the enum's own construction). */
    private static final Map<IncidentStatus, Set<IncidentStatus>> SPEC = Map.ofEntries(
            Map.entry(REPORTED, EnumSet.of(ACKNOWLEDGED, CANCELLED)),
            Map.entry(ACKNOWLEDGED, EnumSet.of(TRIAGED, ESCALATED, CANCELLED)),
            Map.entry(TRIAGED, EnumSet.of(AUTO_RESOLVING, PENDING_AGENCY_ASSIGNMENT, RESOLVED, ESCALATED, CANCELLED)),
            Map.entry(AUTO_RESOLVING, EnumSet.of(REASSIGNING_DRIVER, REROUTING, TRANSFERRING_TO_HUB,
                    AUTO_RESOLUTION_FAILED, ESCALATED, CANCELLED)),
            Map.entry(REASSIGNING_DRIVER, EnumSet.of(AWAITING_HANDOVER, AUTO_RESOLUTION_FAILED, RESOLVED, ESCALATED,
                    CANCELLED)),
            Map.entry(AWAITING_HANDOVER, EnumSet.of(RESOLVED, REASSIGNING_DRIVER, ESCALATED, CANCELLED)),
            Map.entry(REROUTING, EnumSet.of(RESOLVED, AUTO_RESOLUTION_FAILED, ESCALATED, CANCELLED)),
            Map.entry(TRANSFERRING_TO_HUB, EnumSet.of(RESOLVED, AUTO_RESOLUTION_FAILED, ESCALATED, CANCELLED)),
            Map.entry(AUTO_RESOLUTION_FAILED, EnumSet.of(PENDING_AGENCY_ASSIGNMENT, ESCALATED, CANCELLED)),
            Map.entry(PENDING_AGENCY_ASSIGNMENT, EnumSet.of(AGENCY_HANDLING, ESCALATED, CANCELLED)),
            Map.entry(AGENCY_HANDLING, EnumSet.of(WAITING_INTERAGENCY, AWAITING_HANDOVER, PENDING_CONFIRMATION,
                    RESOLVED, ESCALATED, CANCELLED)),
            Map.entry(WAITING_INTERAGENCY, EnumSet.of(INTERAGENCY_IN_PROGRESS, AGENCY_HANDLING, ESCALATED, CANCELLED)),
            Map.entry(INTERAGENCY_IN_PROGRESS, EnumSet.of(AGENCY_HANDLING, RESOLVED, ESCALATED, CANCELLED)),
            Map.entry(ESCALATED, EnumSet.of(ESCALATED, AGENCY_HANDLING, PENDING_CONFIRMATION, RESOLVED, CANCELLED)),
            Map.entry(PENDING_CONFIRMATION, EnumSet.of(RESOLVED, AGENCY_HANDLING, CANCELLED)),
            Map.entry(RESOLVED, EnumSet.of(CLOSED)),
            Map.entry(CLOSED, EnumSet.noneOf(IncidentStatus.class)),
            Map.entry(CANCELLED, EnumSet.noneOf(IncidentStatus.class)));

    @ParameterizedTest
    @EnumSource(IncidentStatus.class)
    @DisplayName("the transition table is exactly the one of spec §1.3")
    void transitionTableMatchesTheSpec(IncidentStatus from) {
        assertThat(SPEC).containsKey(from);
        assertThat(from.allowedTargets()).as("targets of %s", from).isEqualTo(SPEC.get(from));
        for (IncidentStatus to : IncidentStatus.values()) {
            assertThat(from.canTransitionTo(to)).as("%s -> %s", from, to).isEqualTo(SPEC.get(from).contains(to));
        }
    }

    @Test
    @DisplayName("DETECTED is gone: an incident is born REPORTED with an IncidentReported event (I1)")
    void bornReported() {
        assertThat(EnumSet.allOf(IncidentStatus.class)).extracting(Enum::name).doesNotContain("DETECTED");
        Incident i = reported(IncidentType.DRIVER_PHONE_DEAD);
        assertThat(i.getStatus()).isEqualTo(REPORTED);
        assertThat(i.getEventSequence()).isEqualTo(1);
        assertThat(i.getPendingTransitions()).singleElement().satisfies(t -> {
            assertThat(t.from()).isNull();
            assertThat(t.to()).isEqualTo(REPORTED);
            assertThat(t.eventKind()).isEqualTo("REPORTED");
            assertThat(t.trigger()).isEqualTo("REPORT_INCIDENT");
            assertThat(t.actor().type()).isEqualTo(TntEventActor.Type.COURIER);
            assertThat(t.actor().id()).isEqualTo(COURIER.toString());
        });
    }

    @Test
    @DisplayName("every move records its event and bumps the event sequence; a refused move changes nothing")
    void movesRecordEvents() {
        Incident triaged = reported(IncidentType.DRIVER_PHONE_DEAD).acknowledge()
                .triage(IncidentSeverity.MEDIUM, null, null, true);
        assertThat(triaged.getEventSequence()).isEqualTo(3);
        assertThat(triaged.getPendingTransitions()).extracting(IncidentTransition::eventKind)
                .containsExactly("REPORTED", "ACKNOWLEDGED", "TRIAGED");
        assertThat(triaged.getPendingTransitions().get(2).details()).containsEntry("blocking", true);

        assertThatThrownBy(triaged::close).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(triaged::startInterAgencyCooperation).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRIAGED -> INTERAGENCY_IN_PROGRESS");
        assertThatThrownBy(() -> reported(IncidentType.DRIVER_PHONE_DEAD).escalate(1, ActorRole.ADMIN_TIIBNTICK))
                .as("I27 excludes REPORTED").isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("RESOLVED requires a delivery outcome; non-blocking path I6 then I30")
    void resolutionNeedsOutcome() {
        Incident triaged = triaged(false);
        assertThatThrownBy(() -> triaged.resolve(ResolutionMode.MANUAL_AGENCY, null, null, null, "RESOLVE",
                TntEventActor.system())).isInstanceOf(IllegalArgumentException.class);
        Incident resolved = triaged.resolve(ResolutionMode.MANUAL_AGENCY, IncidentDeliveryOutcome.FAILED,
                IncidentResponsibleParty.SENDER, "no answer", "RESOLVE", TntEventActor.system());
        assertThat(resolved.getDeliveryOutcome()).isEqualTo(IncidentDeliveryOutcome.FAILED);
        assertThat(resolved.getResponsibleParty()).isEqualTo(IncidentResponsibleParty.SENDER);
        assertThat(resolved.getPendingTransitions().getLast().details())
                .containsEntry("deliveryOutcome", "FAILED").containsEntry("responsibleParty", "SENDER");
        assertThat(resolved.close().getStatus()).isEqualTo(CLOSED);
    }

    @Test
    @DisplayName("QO-14: propose (I23), contest (I25), propose again, confirm by courier or by the delay (I24)")
    void proposeConfirmContest() {
        Incident handling = triaged(true).pendingAgencyAssignment().startAgencyHandling();
        Incident proposed = handling.proposeResolution(IncidentDeliveryOutcome.RESUMED, IncidentResponsibleParty.NONE,
                "road open again", TntEventActor.of(TntEventActor.Type.AGENCY, UUID.randomUUID()));
        assertThat(proposed.getStatus()).isEqualTo(PENDING_CONFIRMATION);
        assertThat(proposed.getResolutionProposedAt()).isNotNull();

        Incident contested = proposed.contestResolution("still blocked",
                TntEventActor.of(TntEventActor.Type.COURIER, COURIER));
        assertThat(contested.getStatus()).isEqualTo(AGENCY_HANDLING);
        assertThat(contested.getDeliveryOutcome()).isNull();
        assertThat(contested.getResolutionProposedAt()).isNull();

        Incident again = contested.proposeResolution(IncidentDeliveryOutcome.FAILED, IncidentResponsibleParty.RECIPIENT,
                "recipient absent", TntEventActor.of(TntEventActor.Type.AGENCY, UUID.randomUUID()));
        Incident byCourier = again.confirmResolution(TntEventActor.of(TntEventActor.Type.COURIER, COURIER), false);
        assertThat(byCourier.getStatus()).isEqualTo(RESOLVED);
        assertThat(byCourier.getDeliveryOutcome()).isEqualTo(IncidentDeliveryOutcome.FAILED);
        assertThat(byCourier.getResponsibleParty()).isEqualTo(IncidentResponsibleParty.RECIPIENT);
        assertThat(byCourier.getPendingTransitions().getLast().trigger()).isEqualTo("CONFIRM_RESOLUTION");

        Incident byDelay = again.confirmResolution(null, true);
        assertThat(byDelay.getPendingTransitions().getLast().trigger()).isEqualTo("CONFIRMATION_TIMED_OUT");
        assertThat(byDelay.getPendingTransitions().getLast().actor().type()).isEqualTo(TntEventActor.Type.SYSTEM);

        assertThatThrownBy(() -> handling.confirmResolution(null, true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("QO-15: escalated to dispute → IncidentEscalatedToDispute then RESOLVED with outcome DISPUTE")
    void escalatedToDispute() {
        Incident escalated = triaged(true).escalate(1, ActorRole.ADMIN_TIIBNTICK);
        Incident resolved = escalated.recordEscalatedToDispute("transféré en litige")
                .resolve(ResolutionMode.MANUAL_ADMIN_OVERRIDE, IncidentDeliveryOutcome.DISPUTE, null,
                        "transféré en litige", "TRANSFERRED_TO_DISPUTE", TntEventActor.system());
        List<String> kinds = resolved.getPendingTransitions().stream().map(IncidentTransition::eventKind).toList();
        assertThat(kinds).endsWith("ESCALATED", "ESCALATED_TO_DISPUTE", "RESOLVED");
        assertThat(resolved.getResolutionNote()).isEqualTo("transféré en litige");
        assertThat(resolved.isBlockingMission()).isFalse();
    }

    @Test
    @DisplayName("isBlockingMission = active and blocking")
    void blockingMission() {
        assertThat(triaged(true).isBlockingMission()).isTrue();
        assertThat(triaged(false).isBlockingMission()).isFalse();
        assertThat(reported(IncidentType.DRIVER_PHONE_DEAD).isBlockingMission()).as("not triaged yet").isFalse();
        assertThat(triaged(true).cancel("wrong").isBlockingMission()).isFalse();
    }

    @Test
    @DisplayName("QO-12: courier-declarable types, over every type")
    void declarableTypes() {
        Set<IncidentType> excluded = EnumSet.of(IncidentType.DRIVER_GPS_SPOOFING,
                IncidentType.DRIVER_MULTI_ACCOUNT_FRAUD, IncidentType.DRIVER_COLLUSION_FRAUD,
                IncidentType.DRIVER_PHANTOM_DELIVERY, IncidentType.DRIVER_PARCEL_THEFT_ATTEMPT,
                IncidentType.DRIVER_TRAJECTORY_DEVIATION, IncidentType.DRIVER_PROLONGED_STOP_SUSPECT,
                IncidentType.DRIVER_VEHICLE_CHANGE_UNDECLARED, IncidentType.DRIVER_SUBSTITUTION_UNDECLARED,
                IncidentType.DRIVER_NON_RESPONSE_REPEATED, IncidentType.DRIVER_DECEASED,
                IncidentType.HUMAN_DRIVER_DECEASED);
        List<String> prefixes = List.of("DRIVER_", "VEHICLE_", "CLIENT_", "PARCEL_", "GEO_", "RELAY_",
                "REGULATORY_", "HUMAN_");
        for (IncidentType t : IncidentType.values()) {
            boolean expected = !excluded.contains(t) && prefixes.stream().anyMatch(t.name()::startsWith);
            assertThat(t.isDeclarableByCourier()).as(t.name()).isEqualTo(expected);
        }
        assertThat(IncidentType.declarableByCourier()).doesNotContainAnyElementsOf(excluded)
                .noneMatch(t -> t.name().startsWith("SYSTEM_") || t.name().startsWith("AUTOMATION_")
                        || t.name().startsWith("SLA_"));
    }

    @Test
    @DisplayName("QO-2: blocking = forced by the courier, or a blocking severity, or a blocking type")
    void blockingPolicy() {
        IncidentBlockingPolicy policy = new IncidentBlockingPolicy();
        assertThat(policy.isBlocking(IncidentType.GEO_ROAD_FLOODED, IncidentSeverity.LOW, true)).isTrue();
        assertThat(policy.isBlocking(IncidentType.GEO_ROAD_FLOODED, IncidentSeverity.LOW, false)).isFalse();
        assertThat(policy.isBlocking(IncidentType.GEO_ROAD_FLOODED, IncidentSeverity.HIGH, false)).isTrue();
        assertThat(policy.isBlocking(IncidentType.VEHICLE_ENGINE_FAILURE, IncidentSeverity.MEDIUM, false)).isTrue();
        assertThat(policy.isBlocking(IncidentType.DRIVER_PHONE_DEAD, IncidentSeverity.LOW, false)).isTrue();
        assertThat(policy.isBlocking(IncidentType.VEHICLE_TIRE_PUNCTURE, IncidentSeverity.LOW, false)).isFalse();
    }

    @Test
    @DisplayName("reference codes TNT-INC-yyMMdd-10HEX do not collide over 20 000 draws")
    void referenceCodesDoNotCollide() {
        Set<String> codes = new HashSet<>();
        for (int n = 0; n < 20_000; n++) {
            codes.add(reported(IncidentType.DRIVER_PHONE_DEAD).getReferenceCode());
        }
        assertThat(codes).hasSize(20_000).allMatch(c -> c.matches("TNT-INC-\\d{6}-[0-9A-F]{10}"));
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private static Incident reported(IncidentType type) {
        return Incident.create(UUID.randomUUID(), null, PlatformType.GO, UUID.randomUUID(),
                IncidentCategory.DRIVER_DELIVERER, type, "x", COURIER, ActorRole.GO_BIKER, List.of(UUID.randomUUID()));
    }

    private static Incident triaged(boolean blocking) {
        return reported(IncidentType.DRIVER_PHONE_DEAD).acknowledge()
                .triage(IncidentSeverity.MEDIUM, null, null, blocking);
    }
}
