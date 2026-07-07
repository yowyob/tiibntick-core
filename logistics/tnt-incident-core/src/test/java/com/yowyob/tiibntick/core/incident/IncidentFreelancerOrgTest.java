package com.yowyob.tiibntick.core.incident;

import com.yowyob.tiibntick.core.incident.domain.enums.*;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentParticipant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FreelancerOrg  extensions to tnt-incident-core.
 *
 * @author MANFOUO Braun
 */
class IncidentFreelancerOrgTest {

    @Nested
    @DisplayName("Incident.create (backward compat)")
    class IncidentCreate {

        @Test
        @DisplayName("Existing create() should default responsibleOrgId to null")
        void createLegacyHasNullOrgId() {
            UUID tenantId = UUID.randomUUID();
            Incident incident = Incident.create(
                    tenantId, UUID.randomUUID(), PlatformType.AGENCY,
                    UUID.randomUUID(), IncidentCategory.SLA_TIME,
                    IncidentType.SLA_BREACH_TRAFFIC_DELAY,
                    "Test incident", UUID.randomUUID(), ActorRole.SYSTEM,
                    List.of());
            assertThat(incident.getResponsibleOrgId()).isNull();
            assertThat(incident.getResponsibleOrgType()).isNull();
        }
    }

    @Nested
    @DisplayName("Incident.createWithFreelancerOrg")
    class IncidentCreateWithFreelancerOrg {

        @Test
        @DisplayName("Should set responsibleOrgId and type to FREELANCER_ORG")
        void createWithFreelancerOrg() {
            UUID tenantId = UUID.randomUUID();
            String orgId = "FRL-ORG-001";
            Incident incident = Incident.createWithFreelancerOrg(
                    tenantId, null, PlatformType.FREELANCER,
                    UUID.randomUUID(), IncidentCategory.SLA_TIME,
                    IncidentType.SLA_BREACH_TRAFFIC_DELAY,
                    "Freelancer incident", UUID.randomUUID(), ActorRole.SYSTEM,
                    List.of(), orgId, "FREELANCER_ORG");

            assertThat(incident.getResponsibleOrgId()).isEqualTo(orgId);
            assertThat(incident.getResponsibleOrgType()).isEqualTo("FREELANCER_ORG");
            assertThat(incident.getSourcePlatform()).isEqualTo(PlatformType.FREELANCER);
        }

        @Test
        @DisplayName("AGENCY type should be set for agency incidents")
        void createWithAgencyOrg() {
            UUID agencyId = UUID.randomUUID();
            Incident incident = Incident.createWithFreelancerOrg(
                    UUID.randomUUID(), agencyId, PlatformType.AGENCY,
                    UUID.randomUUID(), IncidentCategory.SLA_TIME,
                    IncidentType.SLA_BREACH_TRAFFIC_DELAY,
                    "Agency incident", UUID.randomUUID(), ActorRole.SYSTEM,
                    List.of(), agencyId.toString(), "AGENCY");

            assertThat(incident.getResponsibleOrgType()).isEqualTo("AGENCY");
        }
    }

    @Nested
    @DisplayName("IncidentParticipant — org context")
    class ParticipantOrgContext {

        @Test
        @DisplayName("Legacy of() should have null org context")
        void legacyParticipantHasNullOrg() {
            IncidentParticipant p = IncidentParticipant.of(
                    UUID.randomUUID(), UUID.randomUUID(), ActorRole.FREELANCER_DRIVER);
            assertThat(p.getOrgId()).isNull();
            assertThat(p.getOrgType()).isNull();
        }

        @Test
        @DisplayName("New of() overload should set org context")
        void participantWithOrgContext() {
            String orgId = "FRL-ORG-001";
            IncidentParticipant p = IncidentParticipant.of(
                    UUID.randomUUID(), UUID.randomUUID(), ActorRole.FREELANCER_DRIVER,
                    orgId, "FREELANCER_ORG");
            assertThat(p.getOrgId()).isEqualTo(orgId);
            assertThat(p.getOrgType()).isEqualTo("FREELANCER_ORG");
        }
    }
}
