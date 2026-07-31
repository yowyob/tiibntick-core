package com.yowyob.tiibntick.core.incident.application.service;

import com.yowyob.tiibntick.core.incident.application.query.IncidentRequesterContext;
import com.yowyob.tiibntick.core.incident.application.query.ListIncidentsQuery;
import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentCategory;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentType;
import com.yowyob.tiibntick.core.incident.domain.enums.PlatformType;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.port.outbound.IIncidentBlockchainRepository;
import com.yowyob.tiibntick.core.incident.port.outbound.IIncidentEventLogRepository;
import com.yowyob.tiibntick.core.incident.port.outbound.IIncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IncidentQueryService}'s tenant isolation and actor-level
 * ownership scoping (Go-Freelancer integration hardening — previously this module had
 * zero access control of any kind).
 *
 * <p>A non-privileged caller (no {@code incident:manage}) must only ever see incidents
 * they reported themselves, within their own tenant; a privileged caller sees every
 * incident in their tenant. {@code @RequirePermission} enforcement itself is exercised
 * transitively — this test calls the plain (non-proxied) service, so only the manual
 * tenant/ownership checks in the method bodies are under test here.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IncidentQueryService — tenant isolation and ownership scoping")
class IncidentQueryServiceTest {

    @Mock private IIncidentRepository incidentRepository;
    @Mock private IIncidentEventLogRepository eventLogRepository;
    @Mock private IIncidentBlockchainRepository blockchainRepository;

    private IncidentQueryService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();
    private static final UUID REPORTER_ACTOR_ID = UUID.randomUUID();
    private static final UUID OTHER_ACTOR_ID = UUID.randomUUID();
    private static final UUID AGENCY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new IncidentQueryService(incidentRepository, eventLogRepository, blockchainRepository);
    }

    private Incident buildIncident(UUID tenantId, UUID reportedBy) {
        return Incident.create(
                tenantId, AGENCY_ID, PlatformType.FREELANCER, UUID.randomUUID(),
                IncidentCategory.DRIVER_DELIVERER, IncidentType.FREELANCER_ABANDONED_MID_DELIVERY,
                "description", reportedBy, ActorRole.FREELANCER_DRIVER, List.of());
    }

    @Test
    @DisplayName("getById() - reporter can read their own incident")
    void getById_ownIncident_nonPrivileged_isVisible() {
        Incident incident = buildIncident(TENANT_ID, REPORTER_ACTOR_ID);
        when(incidentRepository.findById(any())).thenReturn(Mono.just(incident));

        IncidentRequesterContext ctx = new IncidentRequesterContext(REPORTER_ACTOR_ID, TENANT_ID, false);

        StepVerifier.create(service.getById(incident.getId(), ctx))
                .expectNextMatches(i -> i.getReportedByActorId().equals(REPORTER_ACTOR_ID))
                .verifyComplete();
    }

    @Test
    @DisplayName("getById() - non-privileged caller cannot read someone else's incident")
    void getById_foreignIncident_nonPrivileged_isHidden() {
        Incident incident = buildIncident(TENANT_ID, REPORTER_ACTOR_ID);
        when(incidentRepository.findById(any())).thenReturn(Mono.just(incident));

        IncidentRequesterContext ctx = new IncidentRequesterContext(OTHER_ACTOR_ID, TENANT_ID, false);

        StepVerifier.create(service.getById(incident.getId(), ctx))
                .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 404)
                .verify();
    }

    @Test
    @DisplayName("getById() - caller from another tenant cannot read the incident, even the reporter")
    void getById_crossTenant_isHidden() {
        Incident incident = buildIncident(TENANT_ID, REPORTER_ACTOR_ID);
        when(incidentRepository.findById(any())).thenReturn(Mono.just(incident));

        IncidentRequesterContext ctx = new IncidentRequesterContext(REPORTER_ACTOR_ID, OTHER_TENANT_ID, true);

        StepVerifier.create(service.getById(incident.getId(), ctx))
                .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 404)
                .verify();
    }

    @Test
    @DisplayName("getById() - privileged caller (agency/support) can read any incident in their tenant")
    void getById_privilegedCaller_seesAnyIncidentInTenant() {
        Incident incident = buildIncident(TENANT_ID, REPORTER_ACTOR_ID);
        when(incidentRepository.findById(any())).thenReturn(Mono.just(incident));

        IncidentRequesterContext ctx = new IncidentRequesterContext(UUID.randomUUID(), TENANT_ID, true);

        StepVerifier.create(service.getById(incident.getId(), ctx))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("listByAgency() - non-privileged caller only sees their own incidents")
    void listByAgency_nonPrivileged_filtersToOwnIncidents() {
        Incident own = buildIncident(TENANT_ID, REPORTER_ACTOR_ID);
        Incident foreign = buildIncident(TENANT_ID, OTHER_ACTOR_ID);
        when(incidentRepository.findByAgencyIdAndCreatedBetween(any(), any(), any()))
                .thenReturn(Flux.just(own, foreign));

        ListIncidentsQuery query = ListIncidentsQuery.builder()
                .tenantId(TENANT_ID).agencyId(AGENCY_ID)
                .requesterActorId(REPORTER_ACTOR_ID).privileged(false)
                .build();

        StepVerifier.create(service.listByAgency(query))
                .expectNextMatches(i -> i.getReportedByActorId().equals(REPORTER_ACTOR_ID))
                .verifyComplete();
    }

    @Test
    @DisplayName("listByAgency() - privileged caller sees every incident in the tenant")
    void listByAgency_privileged_seesEveryIncident() {
        Incident first = buildIncident(TENANT_ID, REPORTER_ACTOR_ID);
        Incident second = buildIncident(TENANT_ID, OTHER_ACTOR_ID);
        when(incidentRepository.findByAgencyIdAndCreatedBetween(any(), any(), any()))
                .thenReturn(Flux.just(first, second));

        ListIncidentsQuery query = ListIncidentsQuery.builder()
                .tenantId(TENANT_ID).agencyId(AGENCY_ID)
                .requesterActorId(UUID.randomUUID()).privileged(true)
                .build();

        StepVerifier.create(service.listByAgency(query))
                .expectNextCount(2)
                .verifyComplete();
    }
}
