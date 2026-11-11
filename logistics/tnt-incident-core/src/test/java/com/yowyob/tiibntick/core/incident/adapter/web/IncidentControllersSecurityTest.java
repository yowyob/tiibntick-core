package com.yowyob.tiibntick.core.incident.adapter.web;

import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.incident.adapter.web.dto.*;
import com.yowyob.tiibntick.core.incident.adapter.web.mapper.IncidentWebMapper;
import com.yowyob.tiibntick.core.incident.application.query.IncidentRequesterContext;
import com.yowyob.tiibntick.core.incident.port.inbound.*;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Seeing an incident does not give the right to run it. The reporter (a livreur) feeds their own case; triage,
 * resolution, closing, assignment and cooperation belong to {@code incident:manage}.
 */
@DisplayName("Incident controllers: reporters feed their case, managers run it")
class IncidentControllersSecurityTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID DRIVER = UUID.randomUUID();
    private static final UUID MANAGER = UUID.randomUUID();
    private static final UUID INCIDENT = UUID.randomUUID();

    private final IReportIncidentUseCase report = mock(IReportIncidentUseCase.class);
    private final IReportDriverWithdrawalUseCase withdrawal = mock(IReportDriverWithdrawalUseCase.class);
    private final ITriageIncidentUseCase triage = mock(ITriageIncidentUseCase.class);
    private final IStartAutoResolutionUseCase autoResolve = mock(IStartAutoResolutionUseCase.class);
    private final IResolveIncidentUseCase resolve = mock(IResolveIncidentUseCase.class);
    private final ICloseIncidentUseCase close = mock(ICloseIncidentUseCase.class);
    private final ICancelIncidentUseCase cancel = mock(ICancelIncidentUseCase.class);
    private final IEscalateIncidentUseCase escalate = mock(IEscalateIncidentUseCase.class);
    private final IAttachEvidenceUseCase evidence = mock(IAttachEvidenceUseCase.class);
    private final IQueryIncidentUseCase query = mock(IQueryIncidentUseCase.class);
    private final IncidentWebMapper mapper = mock(IncidentWebMapper.class);
    private final IAssignReplacementDriverUseCase assign = mock(IAssignReplacementDriverUseCase.class);
    private final IConfirmHandoverUseCase confirm = mock(IConfirmHandoverUseCase.class);
    private final IStartAgencyHandlingUseCase handling = mock(IStartAgencyHandlingUseCase.class);
    private final IInterAgencyCooperationUseCase cooperation = mock(IInterAgencyCooperationUseCase.class);

    private IncidentController incidents;
    private IncidentHandoverController handover;
    private IncidentAgencyController agency;
    private IncidentCooperationController coop;

    private final TntUserIdentity driver =
            new TntUserIdentity(UUID.randomUUID(), TENANT, DRIVER, null, null, Set.of("incident:create", "incident:read"), false);
    private final TntUserIdentity manager =
            new TntUserIdentity(UUID.randomUUID(), TENANT, MANAGER, null, null, Set.of("incident:read", "incident:manage"), false);

    @BeforeEach
    void setUp() {
        incidents = new IncidentController(report, withdrawal, triage, autoResolve, resolve, close, cancel,
                escalate, evidence, query, mapper);
        handover = new IncidentHandoverController(assign, confirm, query);
        agency = new IncidentAgencyController(handling, query, mapper);
        coop = new IncidentCooperationController(cooperation, query);
        // the incident is visible to whoever asks — we are testing the action guards, not visibility
        when(query.getById(any(), any(IncidentRequesterContext.class))).thenReturn(Mono.empty());
    }

    private static void forbidden(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(TntRoleException.class);
    }

    @Test
    @DisplayName("a reporter cannot triage, auto-resolve, resolve or close, and the use cases are never reached")
    void reporterCannotRunTheCase() {
        forbidden(() -> incidents.triage(driver, INCIDENT, TriageRequest.builder().triggeredByActorId(DRIVER).build()));
        forbidden(() -> incidents.startAutoResolution(driver, INCIDENT));
        forbidden(() -> incidents.resolve(driver, INCIDENT, ResolveRequest.builder().resolvedByActorId(DRIVER).build()));
        forbidden(() -> incidents.close(driver, INCIDENT, DRIVER));
        forbidden(() -> incidents.getKpi(driver, UUID.randomUUID(), TENANT));
        verifyNoInteractions(triage, autoResolve, resolve, close);
    }

    @Test
    @DisplayName("a reporter cannot assign a replacement driver, start agency handling or run a cooperation")
    void reporterCannotDispatch() {
        forbidden(() -> handover.assignDriver(driver, INCIDENT, AssignDriverRequest.builder().assignedByActorId(DRIVER).build()));
        forbidden(() -> agency.startHandling(driver, INCIDENT, UUID.randomUUID(), DRIVER, null, null));
        forbidden(() -> coop.request(driver, INCIDENT, UUID.randomUUID(), CooperationRequest.builder().build()));
        forbidden(() -> coop.accept(driver, INCIDENT, UUID.randomUUID(), UUID.randomUUID(), DRIVER, null));
        forbidden(() -> coop.reject(driver, INCIDENT, UUID.randomUUID(), UUID.randomUUID(), DRIVER, "no"));
        forbidden(() -> coop.complete(driver, INCIDENT, UUID.randomUUID(), DRIVER));
        verifyNoInteractions(assign, handling, cooperation);
    }

    @Test
    @DisplayName("a reporter may escalate, cancel and confirm a handover only as themselves")
    void reporterActsAsSelfOnly() {
        UUID somebodyElse = UUID.randomUUID();
        forbidden(() -> incidents.escalate(driver, INCIDENT, EscalateRequest.builder().escalatedByActorId(somebodyElse).reason("x").build()));
        forbidden(() -> incidents.cancel(driver, INCIDENT, somebodyElse, "x"));
        forbidden(() -> handover.confirm(driver, INCIDENT, somebodyElse, true));
        verifyNoInteractions(escalate, cancel, confirm);

        // as themselves they pass the guard and reach the visibility lookup (which here is empty)
        assertThat(incidents.escalate(driver, INCIDENT, EscalateRequest.builder().escalatedByActorId(DRIVER).reason("x").build())).isNotNull();
        assertThat(incidents.cancel(driver, INCIDENT, DRIVER, "x")).isNotNull();
        assertThat(handover.confirm(driver, INCIDENT, DRIVER, true)).isNotNull();
    }

    @Test
    @DisplayName("a manager passes every management guard")
    void managerPasses() {
        assertThat(incidents.triage(manager, INCIDENT, TriageRequest.builder().triggeredByActorId(MANAGER).build())).isNotNull();
        assertThat(incidents.startAutoResolution(manager, INCIDENT)).isNotNull();
        assertThat(incidents.resolve(manager, INCIDENT, ResolveRequest.builder().resolvedByActorId(MANAGER).build())).isNotNull();
        assertThat(incidents.close(manager, INCIDENT, MANAGER)).isNotNull();
        assertThat(handover.assignDriver(manager, INCIDENT, AssignDriverRequest.builder().assignedByActorId(MANAGER).build())).isNotNull();
        assertThat(agency.startHandling(manager, INCIDENT, UUID.randomUUID(), MANAGER, null, null)).isNotNull();
        assertThat(coop.complete(manager, INCIDENT, UUID.randomUUID(), MANAGER)).isNotNull();
        // a manager may act on behalf of someone else (e.g. cancel for a driver)
        assertThat(incidents.cancel(manager, INCIDENT, DRIVER, "driver asked")).isNotNull();
    }

    @Test
    @DisplayName("nobody can report in another tenant or as another actor unless they manage")
    void reportingIsAnchoredToTheCaller() {
        forbidden(() -> incidents.reportIncident(driver, ReportIncidentRequest.builder()
                .tenantId(UUID.randomUUID()).reportedByActorId(DRIVER).build()));
        forbidden(() -> incidents.reportIncident(driver, ReportIncidentRequest.builder()
                .tenantId(TENANT).reportedByActorId(UUID.randomUUID()).build()));
        verifyNoInteractions(report);
    }
}
