package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest;

import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeCommandUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeQueryUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IEvidenceUseCase;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.dto.request.DisputeRequests;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regression test for Audit n°7 · #6 — {@code DisputeController}/{@code EvidenceController}/
 * {@code MediationController} had zero {@code @PreAuthorize}/{@code @RequirePermission} guard
 * on any endpoint.
 *
 * <p>Remediation moved role enforcement to {@code @RequirePermission} on the application
 * service layer (see {@code DisputeCommandServicePermissionEnforcementTest} for that proof —
 * same pattern as {@code WalletControllerPermissionEnforcementTest}), following this
 * codebase's established convention of gating services, not controllers. What remained
 * controller-level is ownership scoping: a non-privileged caller (no {@code dispute:resolve})
 * must not be able to open/comment/withdraw a dispute <em>as</em> a different actor than
 * themselves. These tests exercise that {@code assertActingAsSelf} guard directly on the
 * controller — no Spring context needed, since {@code TntUserIdentity} is a plain parameter.
 *
 * @author MANFOUO Braun
 */
class DisputeControllersSecurityTest {

    private static final UUID SELF_ACTOR_ID = UUID.randomUUID();
    private static final UUID OTHER_ACTOR_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    private static TntUserIdentity nonPrivilegedIdentity() {
        return new TntUserIdentity(UUID.randomUUID(), TENANT_ID, SELF_ACTOR_ID, null, null,
                Set.of("dispute:create"), true);
    }

    private static TntUserIdentity privilegedIdentity() {
        return new TntUserIdentity(UUID.randomUUID(), TENANT_ID, SELF_ACTOR_ID, null, null,
                Set.of("dispute:resolve"), false);
    }

    @Test
    @DisplayName("openDispute() - non-privileged caller impersonating another claimant is rejected")
    void openDispute_impersonation_isRejected() {
        IDisputeCommandUseCase commandUseCase = mock(IDisputeCommandUseCase.class);
        IDisputeQueryUseCase queryUseCase = mock(IDisputeQueryUseCase.class);
        DisputeController controller = new DisputeController(commandUseCase, queryUseCase);

        DisputeRequests.OpenDisputeRequest request = new DisputeRequests.OpenDisputeRequest(
                "PACKAGE_DAMAGED", "MISSION_GO", "NORMAL", OTHER_ACTOR_ID.toString(), "FREELANCER",
                "respondent-1", "AGENCY", "mission-1", "pkg-1", "TRK-1",
                "description", null, null, null);

        StepVerifier.create(Mono.defer(() -> controller.openDispute(nonPrivilegedIdentity(), request)))
                .expectError(TntRoleException.class)
                .verify();

        verifyNoInteractions(commandUseCase);
    }

    @Test
    @DisplayName("openDispute() - non-privileged caller acting as themselves is allowed through")
    void openDispute_actingAsSelf_isAllowed() {
        IDisputeCommandUseCase commandUseCase = mock(IDisputeCommandUseCase.class);
        IDisputeQueryUseCase queryUseCase = mock(IDisputeQueryUseCase.class);
        DisputeController controller = new DisputeController(commandUseCase, queryUseCase);

        Dispute dispute = mock(Dispute.class);
        when(commandUseCase.openDispute(any())).thenReturn(Mono.just(dispute));
        when(dispute.getId()).thenReturn(com.yowyob.tiibntick.core.dispute.domain.model.DisputeId.of(UUID.randomUUID().toString()));
        when(dispute.getReference()).thenReturn(
                com.yowyob.tiibntick.core.dispute.domain.model.DisputeReference.forSequence(1));
        when(dispute.getStatus()).thenReturn(com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus.OPEN);
        when(dispute.getPriority()).thenReturn(com.yowyob.tiibntick.core.dispute.domain.enums.DisputePriority.NORMAL);
        when(dispute.getFiledAt()).thenReturn(java.time.LocalDateTime.now());
        when(dispute.getDeadline()).thenReturn(java.time.LocalDateTime.now().plusDays(3));

        DisputeRequests.OpenDisputeRequest request = new DisputeRequests.OpenDisputeRequest(
                "PACKAGE_DAMAGED", "MISSION_GO", "NORMAL", SELF_ACTOR_ID.toString(), "FREELANCER",
                "respondent-1", "AGENCY", "mission-1", "pkg-1", "TRK-1",
                "description", null, null, null);

        StepVerifier.create(controller.openDispute(nonPrivilegedIdentity(), request))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("openDispute() - privileged caller may open a dispute on behalf of another actor")
    void openDispute_privilegedCaller_bypassesImpersonationGuard() {
        IDisputeCommandUseCase commandUseCase = mock(IDisputeCommandUseCase.class);
        IDisputeQueryUseCase queryUseCase = mock(IDisputeQueryUseCase.class);
        DisputeController controller = new DisputeController(commandUseCase, queryUseCase);

        Dispute dispute = mock(Dispute.class);
        when(commandUseCase.openDispute(any())).thenReturn(Mono.just(dispute));
        when(dispute.getId()).thenReturn(com.yowyob.tiibntick.core.dispute.domain.model.DisputeId.of(UUID.randomUUID().toString()));
        when(dispute.getReference()).thenReturn(
                com.yowyob.tiibntick.core.dispute.domain.model.DisputeReference.forSequence(1));
        when(dispute.getStatus()).thenReturn(com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus.OPEN);
        when(dispute.getPriority()).thenReturn(com.yowyob.tiibntick.core.dispute.domain.enums.DisputePriority.NORMAL);
        when(dispute.getFiledAt()).thenReturn(java.time.LocalDateTime.now());
        when(dispute.getDeadline()).thenReturn(java.time.LocalDateTime.now().plusDays(3));

        DisputeRequests.OpenDisputeRequest request = new DisputeRequests.OpenDisputeRequest(
                "PACKAGE_DAMAGED", "MISSION_GO", "NORMAL", OTHER_ACTOR_ID.toString(), "FREELANCER",
                "respondent-1", "AGENCY", "mission-1", "pkg-1", "TRK-1",
                "description", null, null, null);

        StepVerifier.create(controller.openDispute(privilegedIdentity(), request))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("addComment() - non-privileged caller posting as another author is rejected")
    void addComment_impersonation_isRejected() {
        IDisputeCommandUseCase commandUseCase = mock(IDisputeCommandUseCase.class);
        IDisputeQueryUseCase queryUseCase = mock(IDisputeQueryUseCase.class);
        DisputeController controller = new DisputeController(commandUseCase, queryUseCase);

        DisputeRequests.AddCommentRequest request = new DisputeRequests.AddCommentRequest(
                OTHER_ACTOR_ID.toString(), "USER", "comment text", false);

        StepVerifier.create(Mono.defer(() -> controller.addComment(nonPrivilegedIdentity(), UUID.randomUUID().toString(), request)))
                .expectError(TntRoleException.class)
                .verify();

        verifyNoInteractions(commandUseCase);
    }

    @Test
    @DisplayName("withdraw() - non-privileged caller withdrawing as another claimant is rejected")
    void withdraw_impersonation_isRejected() {
        IDisputeCommandUseCase commandUseCase = mock(IDisputeCommandUseCase.class);
        IDisputeQueryUseCase queryUseCase = mock(IDisputeQueryUseCase.class);
        DisputeController controller = new DisputeController(commandUseCase, queryUseCase);

        DisputeRequests.WithdrawDisputeRequest request = new DisputeRequests.WithdrawDisputeRequest(OTHER_ACTOR_ID.toString());

        StepVerifier.create(Mono.defer(() -> controller.withdraw(nonPrivilegedIdentity(), UUID.randomUUID().toString(), request)))
                .expectError(TntRoleException.class)
                .verify();

        verifyNoInteractions(commandUseCase);
    }

    @Test
    @DisplayName("EvidenceController.submitEvidence() - non-privileged caller submitting as another party is rejected")
    void submitEvidence_impersonation_isRejected() {
        IEvidenceUseCase evidenceUseCase = mock(IEvidenceUseCase.class);
        EvidenceController controller = new EvidenceController(evidenceUseCase);

        DisputeRequests.AddEvidenceRequest request = new DisputeRequests.AddEvidenceRequest(
                OTHER_ACTOR_ID.toString(), "CLAIMANT", "PHOTO", "file-key", "desc", "hash");

        StepVerifier.create(Mono.defer(() -> controller.submitEvidence(nonPrivilegedIdentity(), UUID.randomUUID().toString(), request)))
                .expectError(TntRoleException.class)
                .verify();

        verifyNoInteractions(evidenceUseCase);
    }
}
