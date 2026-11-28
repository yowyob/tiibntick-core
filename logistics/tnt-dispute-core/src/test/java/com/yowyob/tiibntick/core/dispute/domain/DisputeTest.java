package com.yowyob.tiibntick.core.dispute.domain;

import com.yowyob.tiibntick.core.dispute.application.command.*;
import com.yowyob.tiibntick.core.dispute.domain.enums.*;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeStateException;
import com.yowyob.tiibntick.core.dispute.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link Dispute} aggregate root.
 *
 * <p>Validates all state machine transitions, business invariants,
 * and domain event emission.
 *
 * @author MANFOUO Braun
 */
@DisplayName("Dispute Aggregate Root")
class DisputeTest {

    private static final String TENANT = "agency-douala-01";
    private static final String CLAIMANT_ID = "client-abc123";
    private static final String RESPONDENT_ID = "freelancer-xyz789";
    private static final String MEDIATOR_ID = "mediator-001";
    private static final String PACKAGE_ID = "pkg-001";
    private static final String MISSION_ID = "mission-001";
    private static final String TRACKING = "TKG-2025-0042";

    private OpenDisputeCommand buildOpenCmd() {
        return new OpenDisputeCommand(
                TENANT,
                CLAIMANT_ID,
                ClaimantType.CLIENT,
                RESPONDENT_ID,
                RespondentType.FREELANCER,
                DisputeCause.PACKAGE_DAMAGED,
                DisputeCategory.MISSION_GO,
                DisputePriority.HIGH,
                MISSION_ID,
                PACKAGE_ID,
                TRACKING,
                "My package arrived severely damaged. Photos attached.",
                null,
                null,
                false);
    }

    // =========================================================================
    // OPEN
    // =========================================================================

    @Nested
    @DisplayName("open()")
    class OpenTests {

        @Test
        @DisplayName("should create dispute in OPEN status with generated reference")
        void shouldCreateDisputeInOpenStatus() {
            Dispute dispute = Dispute.open(buildOpenCmd(), DisputeReference.forSequence(1));

            assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.OPEN);
            assertThat(dispute.getId()).isNotNull();
            assertThat(dispute.getReference()).isNotNull();
            assertThat(dispute.getReference().getValue()).startsWith("DSP-");
            assertThat(dispute.getTenantId()).isEqualTo(TENANT);
            assertThat(dispute.getClaimantId()).isEqualTo(CLAIMANT_ID);
            assertThat(dispute.getPriority()).isEqualTo(DisputePriority.HIGH);
        }

        @Test
        @DisplayName("should record the LT1 opening, published as DisputeOpen on tnt.dispute.events (lot L8)")
        void shouldRecordTheOpening() {
            Dispute dispute = Dispute.open(buildOpenCmd(), DisputeReference.forSequence(1));

            List<DisputeTransition> pending = dispute.getPendingTransitions();
            assertThat(pending).hasSize(1);
            assertThat(pending.get(0).from()).isNull();
            assertThat(pending.get(0).to()).isEqualTo(DisputeStatus.OPEN);
            assertThat(dispute.getTenantId()).isEqualTo(TENANT);
            assertThat(dispute.getPackageId()).isEqualTo(PACKAGE_ID);
        }

        @Test
        @DisplayName("should compute SLA deadline based on HIGH priority")
        void shouldComputeSlaDeadline() {
            Dispute dispute = Dispute.open(buildOpenCmd(), DisputeReference.forSequence(1));

            assertThat(dispute.getDeadline()).isNotNull();
            assertThat(dispute.getSlaPolicy()).isNotNull();
            assertThat(dispute.getSlaPolicy().getInitialResponseDeadlineHours()).isEqualTo(48);
        }

        @Test
        @DisplayName("should fail if description is blank")
        void shouldFailIfDescriptionBlank() {
            assertThatThrownBy(() -> new OpenDisputeCommand(
                    TENANT, CLAIMANT_ID, ClaimantType.CLIENT,
                    RESPONDENT_ID, RespondentType.FREELANCER,
                    DisputeCause.PACKAGE_DAMAGED, DisputeCategory.MISSION_GO,
                    DisputePriority.NORMAL, MISSION_ID, PACKAGE_ID, TRACKING, "  ",
                    null, null, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("description must not be blank");
        }
    }

    // =========================================================================
    // ASSIGN MEDIATOR
    // =========================================================================

    @Nested
    @DisplayName("assignMediator()")
    class AssignMediatorTests {

        @Test
        @DisplayName("should transition to UNDER_INVESTIGATION when open")
        void shouldTransitionToUnderInvestigation() {
            Dispute dispute = Dispute.open(buildOpenCmd(), DisputeReference.forSequence(1));
            dispute.clearPendingTransitions();

            dispute.assignMediator(MEDIATOR_ID);

            assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.UNDER_INVESTIGATION);
            assertThat(dispute.getAssignedMediatorId()).isEqualTo(MEDIATOR_ID);
            assertThat(dispute.getPendingTransitions()).singleElement()
                    .satisfies(t -> assertThat(t.to()).isEqualTo(DisputeStatus.UNDER_INVESTIGATION));
        }

        @Test
        @DisplayName("should fail if dispute is terminal")
        void shouldFailIfTerminal() {
            Dispute dispute = buildClosedDispute();
            assertThatThrownBy(() -> dispute.assignMediator(MEDIATOR_ID))
                    .isInstanceOf(DisputeStateException.class);
        }
    }

    // =========================================================================
    // ADD EVIDENCE
    // =========================================================================

    @Nested
    @DisplayName("addEvidence()")
    class AddEvidenceTests {

        @Test
        @DisplayName("should add evidence and record the EVIDENCE_ADDED change")
        void shouldAddEvidence() {
            Dispute dispute = Dispute.open(buildOpenCmd(), DisputeReference.forSequence(1));
            dispute.assignMediator(MEDIATOR_ID); // OPEN -> UNDER_INVESTIGATION
            dispute.clearPendingTransitions();

            DisputeEvidence evidence = DisputeEvidence.create(
                    dispute.getId(), CLAIMANT_ID,
                    EvidenceSubmitterType.CLAIMANT, EvidenceType.PHOTO,
                    "minio/photos/dmg-001.jpg", "Front view of damaged corner", null);

            dispute.addEvidence(evidence);

            assertThat(dispute.getEvidences()).hasSize(1);
            assertThat(dispute.getPendingTransitions()).singleElement()
                    .satisfies(t -> assertThat(t.changeKind()).isEqualTo("EVIDENCE_ADDED"));
        }
    }

    // =========================================================================
    // RULE
    // =========================================================================

    @Nested
    @DisplayName("rule()")
    class RuleTests {

        @Test
        @DisplayName("should transition to PENDING_COMPENSATION when compensation required")
        void shouldTransitionToPendingCompensationWhenRequired() {
            Dispute dispute = buildDisputeUnderMediation();
            dispute.clearPendingTransitions();

            CompensationDetails comp = CompensationDetails.approved(
                    new BigDecimal("15000"), "XAF",
                    CompensationMethod.MOBILE_MONEY_MTN, CLAIMANT_ID);

            RuleDisputeCommand cmd = new RuleDisputeCommand(
                    dispute.getId(), TENANT, MEDIATOR_ID,
                    ResolutionType.COMPENSATION_GRANTED, true, comp, "Package confirmed damaged");

            dispute.rule(cmd);

            assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.PENDING_COMPENSATION);
            assertThat(dispute.getResolution()).isNotNull();
            assertThat(dispute.getCompensation()).isNotNull();
            assertThat(dispute.getCompensation().getAmount()).isEqualByComparingTo("15000");
        }

        @Test
        @DisplayName("should transition to CLOSED_RESOLVED when no compensation required")
        void shouldCloseWhenNoCompensation() {
            Dispute dispute = buildDisputeUnderMediation();
            dispute.clearPendingTransitions();

            RuleDisputeCommand cmd = new RuleDisputeCommand(
                    dispute.getId(), TENANT, MEDIATOR_ID,
                    ResolutionType.COMPLAINT_DISMISSED, false, null, "No evidence of damage");

            dispute.rule(cmd);

            assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.CLOSED_RESOLVED);
            assertThat(dispute.getStatus().isTerminal()).isTrue();
        }
    }

    // =========================================================================
    // WITHDRAW
    // =========================================================================

    @Nested
    @DisplayName("withdraw()")
    class WithdrawTests {

        @Test
        @DisplayName("should close as CLOSED_WITHDRAWN when in withdrawable state")
        void shouldWithdraw() {
            Dispute dispute = Dispute.open(buildOpenCmd(), DisputeReference.forSequence(1));
            dispute.clearPendingTransitions();

            dispute.withdraw(CLAIMANT_ID);

            assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.CLOSED_WITHDRAWN);
            assertThat(dispute.getStatus().isTerminal()).isTrue();
        }

        @Test
        @DisplayName("should fail if dispute is already terminal")
        void shouldFailIfAlreadyTerminal() {
            Dispute dispute = buildClosedDispute();
            assertThatThrownBy(() -> dispute.withdraw(CLAIMANT_ID))
                    .isInstanceOf(DisputeStateException.class);
        }
    }

    // =========================================================================
    // PENDING CHANGES — clearPendingTransitions
    // =========================================================================

    @Test
    @DisplayName("clearPendingTransitions() should forget the published changes")
    void shouldClearPendingTransitions() {
        Dispute dispute = Dispute.open(buildOpenCmd(), DisputeReference.forSequence(1));
        assertThat(dispute.getPendingTransitions()).isNotEmpty();

        dispute.clearPendingTransitions();

        assertThat(dispute.getPendingTransitions()).isEmpty();
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private Dispute buildDisputeUnderMediation() {
        Dispute dispute = Dispute.open(buildOpenCmd(), DisputeReference.forSequence(1));
        dispute.clearPendingTransitions();
        dispute.assignMediator(MEDIATOR_ID);
        dispute.clearPendingTransitions();
        dispute.startMediation();
        dispute.clearPendingTransitions();
        return dispute;
    }

    private Dispute buildClosedDispute() {
        Dispute dispute = Dispute.open(buildOpenCmd(), DisputeReference.forSequence(1));
        dispute.withdraw(CLAIMANT_ID);
        dispute.clearPendingTransitions();
        return dispute;
    }
}
