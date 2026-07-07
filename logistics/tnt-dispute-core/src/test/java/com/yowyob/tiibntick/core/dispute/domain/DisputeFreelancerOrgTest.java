package com.yowyob.tiibntick.core.dispute.domain;

import com.yowyob.tiibntick.core.dispute.application.command.OpenDisputeAgainstFreelancerOrgCommand;
import com.yowyob.tiibntick.core.dispute.application.command.OpenDisputeCommand;
import com.yowyob.tiibntick.core.dispute.domain.enums.*;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FreelancerOrg  extensions to the Dispute domain model.
 *
 * @author MANFOUO Braun
 */
class DisputeFreelancerOrgTest {

    @Nested
    @DisplayName("RespondentType.FREELANCER_ORG")
    class FreelancerOrgRespondent {

        @Test
        @DisplayName("FREELANCER_ORG enum value must exist")
        void freelancerOrgValueExists() {
            assertThat(RespondentType.valueOf("FREELANCER_ORG")).isEqualTo(RespondentType.FREELANCER_ORG);
        }

        @Test
        @DisplayName("HUB_POINT enum value must exist")
        void hubPointValueExists() {
            assertThat(RespondentType.valueOf("HUB_POINT")).isEqualTo(RespondentType.HUB_POINT);
        }

        @Test
        @DisplayName("LINK_NETWORK enum value must exist")
        void linkNetworkValueExists() {
            assertThat(RespondentType.valueOf("LINK_NETWORK")).isEqualTo(RespondentType.LINK_NETWORK);
        }

        @Test
        @DisplayName("Legacy enum values must be preserved (backward compat)")
        void legacyValuesPreserved() {
            assertThat(RespondentType.valueOf("FREELANCER")).isEqualTo(RespondentType.FREELANCER);
            assertThat(RespondentType.valueOf("AGENCY")).isEqualTo(RespondentType.AGENCY);
            assertThat(RespondentType.valueOf("PLATFORM")).isEqualTo(RespondentType.PLATFORM);
        }
    }

    @Nested
    @DisplayName("OpenDisputeAgainstFreelancerOrgCommand")
    class OpenAgainstFreelancerOrg {

        @Test
        @DisplayName("toOpenDisputeCommand should set FREELANCER_ORG respondent type")
        void convertsToStandardCommand() {
            OpenDisputeAgainstFreelancerOrgCommand cmd = new OpenDisputeAgainstFreelancerOrgCommand(
                    "tenant-1", "client-1", ClaimantType.CLIENT,
                    "FRL-ORG-001", "OWNER-ACTOR-001",
                    "SUB-DELIVERER-001",
                    DisputeCause.PACKAGE_DAMAGED, DisputeCategory.MISSION_GO,
                    DisputePriority.NORMAL, "MISSION-001", "PKG-001", "TRK-001",
                    "Package arrived damaged — fragile items broken");

            OpenDisputeCommand std = cmd.toOpenDisputeCommand();

            assertThat(std.respondentType()).isEqualTo(RespondentType.FREELANCER_ORG);
            assertThat(std.respondentOrgId()).isEqualTo("FRL-ORG-001");
            assertThat(std.respondentId()).isEqualTo("OWNER-ACTOR-001");
            assertThat(std.impliedSubDelivererId()).isEqualTo("SUB-DELIVERER-001");
            assertThat(std.subDelivererInvolved()).isTrue();
        }

        @Test
        @DisplayName("Null subDelivererId should set subDelivererInvolved=false")
        void nullSubDelivererMeansFalse() {
            OpenDisputeAgainstFreelancerOrgCommand cmd = new OpenDisputeAgainstFreelancerOrgCommand(
                    "tenant-1", "client-1", ClaimantType.CLIENT,
                    "FRL-ORG-001", "OWNER-001", null,
                    DisputeCause.DELIVERY_DELAYED, DisputeCategory.MISSION_GO,
                    DisputePriority.LOW, "M-001", null, null, "Late delivery");

            OpenDisputeCommand std = cmd.toOpenDisputeCommand();
            assertThat(std.subDelivererInvolved()).isFalse();
            assertThat(std.impliedSubDelivererId()).isNull();
        }
    }

    @Nested
    @DisplayName("Dispute.open with FreelancerOrg context")
    class DisputeOpenFreelancerOrg {

        @Test
        @DisplayName("Should persist respondentOrgId and impliedSubDelivererId")
        void shouldPersistFreelancerOrgContext() {
            OpenDisputeCommand cmd = new OpenDisputeCommand(
                    "tenant-1", "client-1", ClaimantType.CLIENT,
                    "owner-actor-1", RespondentType.FREELANCER_ORG,
                    DisputeCause.PACKAGE_DAMAGED, DisputeCategory.MISSION_GO,
                    DisputePriority.NORMAL, "M-001", "PKG-001", "TRK-001",
                    "Damaged package",
                    "FRL-ORG-001", "SUB-001", true);

            Dispute dispute = Dispute.open(cmd);

            assertThat(dispute.getRespondentType()).isEqualTo(RespondentType.FREELANCER_ORG);
            assertThat(dispute.getRespondentOrgId()).isEqualTo("FRL-ORG-001");
            assertThat(dispute.getImpliedSubDelivererId()).isEqualTo("SUB-001");
            assertThat(dispute.getSubDelivererInvolved()).isTrue();
        }
    }
}
