package com.yowyob.tiibntick.core.incident;

/**
 * Unit tests for the Incident aggregate root and domain services. Zero Spring context required.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */


import com.yowyob.tiibntick.core.incident.domain.enums.*;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.service.IncidentTriageService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class IncidentDomainTest {

    private final IncidentTriageService triageService = new IncidentTriageService();

    @Test
    void createIncident_shouldSetCorrectInitialState() {
        UUID tenantId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        UUID reportedBy = UUID.randomUUID();

        Incident incident = Incident.create(
                tenantId, agencyId, PlatformType.AGENCY, missionId,
                IncidentCategory.DRIVER_DELIVERER, IncidentType.DRIVER_VOLUNTARY_WITHDRAWAL_BEFORE_PICKUP,
                "Driver withdrew before pickup", reportedBy, ActorRole.AGENCY_MANAGER,
                List.of(UUID.randomUUID())
        );

        assertThat(incident.getId()).isNotNull();
        assertThat(incident.getReferenceCode()).startsWith("TNT-INC-");
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.DETECTED);
        assertThat(incident.getSeverity()).isEqualTo(IncidentSeverity.MEDIUM);
        assertThat(incident.isMultiParcelIncident()).isFalse();
    }

    @Test
    void acknowledge_shouldTransitionToAcknowledged() {
        Incident incident = buildSampleIncident();
        Incident acked = incident.acknowledge();
        assertThat(acked.getStatus()).isEqualTo(IncidentStatus.ACKNOWLEDGED);
        assertThat(acked.getAcknowledgedAt()).isNotNull();
    }

    @Test
    void multiParcelIncident_shouldCreateOwnBlockchainChain() {
        Incident incident = Incident.create(
                UUID.randomUUID(), UUID.randomUUID(), PlatformType.AGENCY, UUID.randomUUID(),
                IncidentCategory.DRIVER_DELIVERER, IncidentType.DRIVER_MEDICAL_EMERGENCY,
                "Emergency", UUID.randomUUID(), ActorRole.AGENCY_MANAGER,
                List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        );
        assertThat(incident.isMultiParcelIncident()).isTrue();
        assertThat(incident.requiresOwnBlockchainChain()).isTrue();
    }

    @Test
    void closedIncident_shouldNotBeTransitionable() {
        Incident incident = buildSampleIncident()
                .acknowledge()
                .resolve(ResolutionMode.MANUAL_AGENCY)
                .close();

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(incident.getStatus().isTerminal()).isTrue();
        assertThatThrownBy(incident::acknowledge)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void triageService_shouldMapIncidentTypeToCorrectCategory() {
        assertThat(triageService.deriveCategory(IncidentType.DRIVER_PHONE_DEAD))
                .isEqualTo(IncidentCategory.DRIVER_DELIVERER);
        assertThat(triageService.deriveCategory(IncidentType.VEHICLE_ENGINE_FAILURE))
                .isEqualTo(IncidentCategory.VEHICLE);
        assertThat(triageService.deriveCategory(IncidentType.PARCEL_PHYSICALLY_DAMAGED))
                .isEqualTo(IncidentCategory.PARCEL_CARGO);
        assertThat(triageService.deriveCategory(IncidentType.GEO_ROAD_FLOODED))
                .isEqualTo(IncidentCategory.GEOGRAPHIC);
    }

    @Test
    void triageService_shouldAssignFatalSeverityForHumanCasualties() {
        assertThat(triageService.determineSeverity(IncidentType.HUMAN_DRIVER_DECEASED, IncidentCategory.HUMAN_CRITICAL))
                .isEqualTo(IncidentSeverity.FATAL);
        assertThat(triageService.determineSeverity(IncidentType.VEHICLE_FIRE, IncidentCategory.VEHICLE))
                .isEqualTo(IncidentSeverity.FATAL);
    }

    private Incident buildSampleIncident() {
        return Incident.create(
                UUID.randomUUID(), UUID.randomUUID(), PlatformType.AGENCY, UUID.randomUUID(),
                IncidentCategory.DRIVER_DELIVERER, IncidentType.DRIVER_PHONE_DEAD,
                "Driver unreachable", UUID.randomUUID(), ActorRole.AGENCY_MANAGER,
                List.of(UUID.randomUUID())
        );
    }
}
