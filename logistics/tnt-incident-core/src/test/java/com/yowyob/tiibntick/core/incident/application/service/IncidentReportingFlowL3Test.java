package com.yowyob.tiibntick.core.incident.application.service;

import com.yowyob.tiibntick.core.incident.application.command.ReportIncidentCommand;
import com.yowyob.tiibntick.core.incident.config.IncidentLifecycleProperties;
import com.yowyob.tiibntick.core.incident.domain.enums.*;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.service.IncidentBlockingPolicy;
import com.yowyob.tiibntick.core.incident.domain.service.IncidentTriageService;
import com.yowyob.tiibntick.core.incident.port.outbound.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Lot L3, declaration flow (rows I1–I5, D13–D15) and resolution effects (D16/D20, unfreeze), with the ports mocked.
 */
class IncidentReportingFlowL3Test {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID MISSION = UUID.randomUUID();
    private static final UUID COURIER = UUID.randomUUID();

    private final IIncidentRepository repo = mock(IIncidentRepository.class);
    private final IIncidentEventLogRepository logs = mock(IIncidentEventLogRepository.class);
    private final INotificationPort notifications = mock(INotificationPort.class);
    private final IDeliveryStatusPort mission = mock(IDeliveryStatusPort.class);
    private final IBlockchainAuditPort chain = mock(IBlockchainAuditPort.class);
    private final IPaymentFreezePort freeze = mock(IPaymentFreezePort.class);
    private final IDriverAvailabilityPort drivers = mock(IDriverAvailabilityPort.class);
    private final IRouteOptimizerPort routes = mock(IRouteOptimizerPort.class);
    private final IIncidentDriverReplacementRepository replacements = mock(IIncidentDriverReplacementRepository.class);
    private final List<Incident> saved = new ArrayList<>();

    private IncidentReportingService reporting;
    private IncidentResolutionEffects effects;

    @BeforeEach
    void setUp() {
        when(repo.save(any())).thenAnswer(inv -> {
            Incident i = inv.getArgument(0);
            saved.add(i);
            return Mono.just(i.toBuilder().pendingTransitions(new ArrayList<>()).build());
        });
        when(logs.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(notifications.notifyAgency(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        when(notifications.notifyActor(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        when(chain.writeIncidentEvent(any(), any(), any(), any())).thenReturn(Mono.just("tx"));
        when(mission.isAssignedCourier(MISSION, COURIER)).thenReturn(Mono.just(true));
        when(mission.markIncidentReported(any(), any())).thenReturn(Mono.empty());
        when(mission.pauseDelivery(any(), any())).thenReturn(Mono.empty());
        when(mission.resumeDelivery(any(), anyString(), any(), any())).thenReturn(Mono.empty());
        when(mission.failDelivery(any(), any(), any())).thenReturn(Mono.empty());
        when(freeze.freezePayment(any(), any())).thenReturn(Mono.empty());
        when(freeze.unfreezePayment(any(), any())).thenReturn(Mono.empty());
        when(drivers.findEligibleReplacementDrivers(any(), any(), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(Flux.empty());
        when(routes.findNearestHub(anyDouble(), anyDouble(), any())).thenReturn(Mono.empty());

        effects = new IncidentResolutionEffects(mission, freeze);
        IncidentAutoResolutionService auto = new IncidentAutoResolutionService(repo, replacements, logs,
                notifications, freeze, drivers, mock(IVehicleCompatibilityPort.class), routes, mission, chain, effects,
                new IncidentLifecycleProperties());
        reporting = new IncidentReportingService(repo, logs, notifications, mission, chain,
                new IncidentTriageService(), new IncidentBlockingPolicy(), auto);
    }

    private ReportIncidentCommand command(IncidentType type, UUID agency, boolean forced) {
        return ReportIncidentCommand.builder().tenantId(TENANT).agencyId(agency).missionId(MISSION)
                .platform(agency == null ? PlatformType.GO : PlatformType.AGENCY).type(type).description("x")
                .reportedByActorId(COURIER).reportedByRole(ActorRole.GO_BIKER).currentLat(3.85).currentLng(11.5)
                .blockingForced(forced).build();
    }

    @Test
    @DisplayName("non-blocking: REPORTED → D13 → ACKNOWLEDGED, TRIAGED → D14 (INCIDENT_NOT_BLOCKING); stays TRIAGED")
    void nonBlockingDeclaration() {
        Incident result = reporting.execute(command(IncidentType.VEHICLE_TIRE_PUNCTURE, null, false)).block();

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.TRIAGED);
        assertThat(result.isBlocking()).isFalse();
        InOrder order = inOrder(mission);
        order.verify(mission).isAssignedCourier(MISSION, COURIER);
        order.verify(mission).markIncidentReported(eq(MISSION), any());
        order.verify(mission).resumeDelivery(MISSION, IDeliveryStatusPort.TRIGGER_INCIDENT_NOT_BLOCKING, null, null);
        verify(mission, never()).pauseDelivery(any(), any());
        verify(freeze, never()).freezePayment(any(), any());
        assertThat(saved.get(0).getPendingTransitions()).extracting(t -> t.eventKind()).containsExactly("REPORTED");
        assertThat(saved.get(1).getPendingTransitions()).extracting(t -> t.eventKind())
                .containsExactly("ACKNOWLEDGED", "TRIAGED");
        assertThat(result.getGeoSnapshot().getLatitude()).isEqualTo(3.85);
    }

    @Test
    @DisplayName("forced blocking on an agency delivery: D15, payment frozen, PENDING_AGENCY_ASSIGNMENT (I5)")
    void blockingAgencyDeclaration() {
        Incident result = reporting.execute(command(IncidentType.GEO_ROAD_FLOODED, UUID.randomUUID(), true)).block();

        assertThat(result.isBlocking()).isTrue();
        assertThat(result.getStatus()).isEqualTo(IncidentStatus.PENDING_AGENCY_ASSIGNMENT);
        verify(mission).pauseDelivery(eq(MISSION), any());
        verify(mission, never()).resumeDelivery(any(), anyString(), any(), any());
        verify(freeze).freezePayment(eq(MISSION), any());
    }

    @Test
    @DisplayName("blocking without agency, no replacement nor hub: I4 → I7 → I10 → I11 (support)")
    void blockingWithoutAgencyFallsToSupport() {
        Incident result = reporting.execute(command(IncidentType.DRIVER_PHONE_DEAD, null, false)).block();

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.PENDING_AGENCY_ASSIGNMENT);
        assertThat(saved).flatExtracting(Incident::getPendingTransitions).extracting(t -> t.eventKind())
                .containsExactly("REPORTED", "ACKNOWLEDGED", "TRIAGED", "AUTO_RESOLVING", "REASSIGNING_DRIVER",
                        "AUTO_RESOLUTION_FAILED", "PENDING_AGENCY_ASSIGNMENT");
        verify(mission).pauseDelivery(eq(MISSION), any());
    }

    @Test
    @DisplayName("I18 by the TiiBnTick support: an incident without agency is taken without agencyId, no agency notified")
    void supportTakesAnIncidentWithoutAgency() {
        Incident pending = reporting.execute(command(IncidentType.DRIVER_PHONE_DEAD, null, false)).block();
        when(repo.findById(pending.getId())).thenReturn(Mono.just(pending));
        IncidentAgencyManagementService management = new IncidentAgencyManagementService(repo, logs,
                notifications, mission, mock(com.yowyob.tiibntick.core.incident.port.outbound.IActorReputationPort.class),
                effects, new IncidentLifecycleProperties());
        UUID agent = UUID.randomUUID();
        UUID handler = UUID.randomUUID();

        Incident handling = management.execute(com.yowyob.tiibntick.core.incident.application.command
                .StartAgencyHandlingCommand.builder().incidentId(pending.getId()).initiatedByActorId(agent)
                .assignedToActorId(handler).build()).block();

        assertThat(handling.getStatus()).isEqualTo(IncidentStatus.AGENCY_HANDLING);
        assertThat(saved.get(saved.size() - 1).getPendingTransitions()).extracting(t -> t.eventKind())
                .containsExactly("AGENCY_HANDLING", "HANDLER_ASSIGNED");
        assertThat(saved.get(saved.size() - 1).getPendingTransitions().get(0).actor().type())
                .isEqualTo(com.yowyob.tiibntick.common.kafka.TntEventActor.Type.SUPPORT);
        verify(notifications, never()).notifyAgency(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("I1 guards: a non-declarable type (QO-12) and a courier who is not the assigned one are refused")
    void declarationGuards() {
        assertThatThrownBy(() -> reporting.execute(command(IncidentType.DRIVER_GPS_SPOOFING, null, false)).block())
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("422");
        when(mission.isAssignedCourier(MISSION, COURIER)).thenReturn(Mono.just(false));
        assertThatThrownBy(() -> reporting.execute(command(IncidentType.GEO_ROAD_FLOODED, null, false)).block())
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        verify(repo, never()).save(any());
        verify(mission, never()).markIncidentReported(any(), any());
    }

    @Test
    @DisplayName("effects: RESUMED resumes a blocked delivery (D16) and unfreezes; FAILED fails it (D20); DISPUTE nothing")
    void resolutionEffects() {
        Incident blocking = Incident.create(TENANT, null, PlatformType.GO, MISSION, IncidentCategory.DRIVER_DELIVERER,
                        IncidentType.DRIVER_PHONE_DEAD, "x", COURIER, ActorRole.GO_BIKER, List.of())
                .acknowledge().triage(IncidentSeverity.MEDIUM, null, null, true);

        effects.afterResolved(blocking.resolve(ResolutionMode.MANUAL_AGENCY, IncidentDeliveryOutcome.RESUMED, null,
                null, "RESOLVE", null), null, null).block();
        verify(mission).resumeDelivery(MISSION, IDeliveryStatusPort.TRIGGER_INCIDENT_RESOLVED, null, null);
        verify(freeze, times(1)).unfreezePayment(eq(MISSION), any());

        effects.afterResolved(blocking.resolve(ResolutionMode.MANUAL_AGENCY, IncidentDeliveryOutcome.FAILED,
                IncidentResponsibleParty.RECIPIENT, null, "RESOLVE", null), null, null).block();
        verify(mission).failDelivery(eq(MISSION), any(), eq("RECIPIENT"));
        verify(freeze, times(2)).unfreezePayment(eq(MISSION), any());

        effects.afterResolved(blocking.resolve(ResolutionMode.MANUAL_AGENCY, IncidentDeliveryOutcome.DISPUTE, null,
                null, "RESOLVE", null), null, null).block();
        verify(freeze, times(2)).unfreezePayment(eq(MISSION), any());

        effects.afterCancelled(blocking.cancel("wrong"), "wrong").block();
        verify(mission).resumeDelivery(MISSION, IDeliveryStatusPort.TRIGGER_INCIDENT_CANCELLED, null, null);
        verify(freeze, times(3)).unfreezePayment(eq(MISSION), any());
    }
}
