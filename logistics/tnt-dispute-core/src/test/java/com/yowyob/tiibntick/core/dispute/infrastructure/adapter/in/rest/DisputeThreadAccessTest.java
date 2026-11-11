package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest;

import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.dispute.application.command.AddCommentCommand;
import com.yowyob.tiibntick.core.dispute.application.command.OpenDisputeCommand;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeCommandUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeQueryUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IEvidenceUseCase;
import com.yowyob.tiibntick.core.dispute.application.query.GetDisputeQuery;
import com.yowyob.tiibntick.core.dispute.domain.enums.*;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeNotFoundException;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeComment;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeReference;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.dto.request.DisputeRequests;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.mapper.DisputeRestMapper;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Who can read and write a dispute's evidence and conversation")
class DisputeThreadAccessTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CLAIMANT = UUID.randomUUID();
    private static final UUID RESPONDENT = UUID.randomUUID();
    private static final UUID OUTSIDER = UUID.randomUUID();
    private static final UUID MEDIATOR = UUID.randomUUID();

    private final IDisputeCommandUseCase commands = mock(IDisputeCommandUseCase.class);
    private final IDisputeQueryUseCase queries = mock(IDisputeQueryUseCase.class);
    private final IEvidenceUseCase evidences = mock(IEvidenceUseCase.class);
    private final DisputeController disputes = new DisputeController(commands, queries);
    private final EvidenceController evidenceController = new EvidenceController(evidences, queries);

    private static TntUserIdentity party(UUID actor) {
        return new TntUserIdentity(UUID.randomUUID(), TENANT, actor, null, null, Set.of("dispute:create", "dispute:read"), false);
    }

    private static TntUserIdentity mediator() {
        return new TntUserIdentity(UUID.randomUUID(), TENANT, MEDIATOR, null, null,
                Set.of("dispute:read", "dispute:resolve"), false);
    }

    private Dispute dispute() {
        Dispute d = Dispute.open(new OpenDisputeCommand(
                TENANT.toString(), CLAIMANT.toString(), ClaimantType.CLIENT, RESPONDENT.toString(), RespondentType.FREELANCER,
                DisputeCause.PACKAGE_DAMAGED, DisputeCategory.MISSION_GO, DisputePriority.HIGH,
                "mission-1", "pkg-1", "TKG-1", "desc", null, null, false), DisputeReference.forSequence(1));
        d.addComment(DisputeComment.post(d.getId(), MEDIATOR.toString(), CommentAuthorType.MEDIATOR, "note interne", true));
        d.addComment(DisputeComment.post(d.getId(), MEDIATOR.toString(), CommentAuthorType.MEDIATOR, "message public", false));
        return d;
    }

    private void visibleTo(String requester, boolean privileged, Dispute d) {
        when(queries.getDispute(any(GetDisputeQuery.class))).thenAnswer(inv -> {
            GetDisputeQuery q = inv.getArgument(0);
            boolean ok = q.privileged() || requester.equals(q.requesterId());
            return ok ? Mono.just(d) : Mono.error(new DisputeNotFoundException(q.disputeId()));
        });
    }

    @Test
    @DisplayName("an outsider cannot list a dispute's evidence; the evidence use case is never reached")
    void outsiderCannotListEvidence() {
        Dispute d = dispute();
        visibleTo(CLAIMANT.toString(), false, d);
        StepVerifier.create(evidenceController.getEvidences(party(OUTSIDER), d.getId().getValue()))
                .expectError(DisputeNotFoundException.class).verify();
        verifyNoInteractions(evidences);
    }

    @Test
    @DisplayName("an outsider cannot add evidence to someone else's dispute even when naming themselves")
    void outsiderCannotSubmitEvidence() {
        Dispute d = dispute();
        visibleTo(CLAIMANT.toString(), false, d);
        var request = new DisputeRequests.AddEvidenceRequest(OUTSIDER.toString(), "CLAIMANT", "PHOTO", "k", "d", "h");
        StepVerifier.create(evidenceController.submitEvidence(party(OUTSIDER), d.getId().getValue(), request))
                .expectError(DisputeNotFoundException.class).verify();
        verifyNoInteractions(evidences);
    }

    @Test
    @DisplayName("the claimant and the mediator read the evidence")
    void partiesAndMediatorListEvidence() {
        Dispute d = dispute();
        visibleTo(CLAIMANT.toString(), false, d);
        when(evidences.getEvidenceForDispute(any(), any())).thenReturn(Flux.empty());
        StepVerifier.create(evidenceController.getEvidences(party(CLAIMANT), d.getId().getValue())).verifyComplete();
        StepVerifier.create(evidenceController.getEvidences(mediator(), d.getId().getValue())).verifyComplete();
        verify(evidences, org.mockito.Mockito.times(2)).getEvidenceForDispute(any(), any());
    }

    @Test
    @DisplayName("a party's comment is stored as its own side and never as an internal note, whatever the payload says")
    void partyCommentIsNormalised() {
        Dispute d = dispute();
        visibleTo(RESPONDENT.toString(), false, d);
        when(commands.addComment(any())).thenReturn(Mono.just(d));
        var forged = new DisputeRequests.AddCommentRequest(RESPONDENT.toString(), "MEDIATOR", "je suis le médiateur", true);

        StepVerifier.create(disputes.addComment(party(RESPONDENT), d.getId().getValue(), forged))
                .expectNextCount(1).verifyComplete();

        ArgumentCaptor<AddCommentCommand> captor = ArgumentCaptor.forClass(AddCommentCommand.class);
        verify(commands).addComment(captor.capture());
        assertThat(captor.getValue().authorType()).isEqualTo(CommentAuthorType.RESPONDENT);
        assertThat(captor.getValue().isInternal()).isFalse();
    }

    @Test
    @DisplayName("an outsider cannot comment on a dispute they cannot see")
    void outsiderCannotComment() {
        Dispute d = dispute();
        visibleTo(CLAIMANT.toString(), false, d);
        var request = new DisputeRequests.AddCommentRequest(OUTSIDER.toString(), "CLAIMANT", "hello", false);
        StepVerifier.create(disputes.addComment(party(OUTSIDER), d.getId().getValue(), request))
                .expectError(DisputeNotFoundException.class).verify();
        verify(commands, never()).addComment(any());
    }

    @Test
    @DisplayName("a mediator may write an internal note under their own role")
    void mediatorInternalNote() {
        Dispute d = dispute();
        when(commands.addComment(any())).thenReturn(Mono.just(d));
        var note = new DisputeRequests.AddCommentRequest(MEDIATOR.toString(), "MEDIATOR", "à surveiller", true);
        StepVerifier.create(disputes.addComment(mediator(), d.getId().getValue(), note)).expectNextCount(1).verifyComplete();
        ArgumentCaptor<AddCommentCommand> captor = ArgumentCaptor.forClass(AddCommentCommand.class);
        verify(commands).addComment(captor.capture());
        assertThat(captor.getValue().isInternal()).isTrue();
    }

    @Test
    @DisplayName("the detail hides internal notes from a party and shows them to the mediator")
    void internalNotesAreMediatorOnly() {
        Dispute d = dispute();
        assertThat(DisputeRestMapper.toDetailResponse(d).comments())
                .extracting(c -> c.content()).containsExactly("message public");
        assertThat(DisputeRestMapper.toDetailResponse(d, false).comments()).hasSize(1);
        assertThat(DisputeRestMapper.toDetailResponse(d, true).comments())
                .extracting(c -> c.content()).containsExactly("note interne", "message public");
    }

    @Test
    @DisplayName("an organisation's disputes are for its members and mediators only")
    void organisationDisputesAreScoped() {
        UUID orgId = UUID.randomUUID();
        when(queries.findDisputesByFreelancerOrg(any(), any(), any())).thenReturn(Flux.empty());
        TntUserIdentity member = new TntUserIdentity(UUID.randomUUID(), TENANT, CLAIMANT, orgId, null, Set.of("dispute:read"), true);
        TntUserIdentity stranger = new TntUserIdentity(UUID.randomUUID(), TENANT, OUTSIDER, UUID.randomUUID(), null, Set.of("dispute:read"), true);

        StepVerifier.create(disputes.getDisputesByFreelancerOrg(member, orgId.toString(), null)).verifyComplete();
        StepVerifier.create(disputes.getDisputesByFreelancerOrg(mediator(), orgId.toString(), null)).verifyComplete();
        assertThat(Assertions.assertThrows(TntRoleException.class,
                () -> disputes.getDisputesByFreelancerOrg(stranger, orgId.toString(), null))).isNotNull();
        assertThat(Assertions.assertThrows(TntRoleException.class,
                () -> disputes.getDisputeStatsByOrg(stranger, orgId.toString()))).isNotNull();
    }

    @Test
    @DisplayName("a party can only open a dispute on a mission they took part in; a mediator is not subject to it")
    void openingRequiresParticipation() {
        var port = mock(com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeParticipationPort.class);
        when(port.isParticipant(any(), any(), any(), any())).thenReturn(Mono.just(false));
        disputes.setParticipation(port);
        when(commands.openDispute(any())).thenReturn(Mono.just(dispute()));
        var request = new DisputeRequests.OpenDisputeRequest("PACKAGE_DAMAGED", "MISSION_GO", "HIGH",
                OUTSIDER.toString(), "CLIENT", RESPONDENT.toString(), "FREELANCER", "mission-1", "pkg-1", "TKG-1",
                "desc", null, null, false);

        StepVerifier.create(disputes.openDispute(party(OUTSIDER), request)).expectError(TntRoleException.class).verify();
        verify(commands, never()).openDispute(any());

        when(port.isParticipant(any(), any(), any(), any())).thenReturn(Mono.just(true));
        StepVerifier.create(disputes.openDispute(party(OUTSIDER), request)).expectNextCount(1).verifyComplete();
    }

    @Test
    @DisplayName("escalation and closure are attributed to the authenticated mediator, whatever name the payload carries")
    void mediatorActionsAreAttributedToTheCaller() {
        var mediation = new MediationController(commands);
        Dispute d = dispute();
        when(commands.escalateDispute(any())).thenReturn(Mono.just(d));
        when(commands.closeDispute(any())).thenReturn(Mono.just(d));

        StepVerifier.create(mediation.escalate(mediator(), d.getId().getValue(),
                new DisputeRequests.EscalateDisputeRequest("someone-else", "complex", null))).expectNextCount(1).verifyComplete();
        ArgumentCaptor<com.yowyob.tiibntick.core.dispute.application.command.EscalateDisputeCommand> esc =
                ArgumentCaptor.forClass(com.yowyob.tiibntick.core.dispute.application.command.EscalateDisputeCommand.class);
        verify(commands).escalateDispute(esc.capture());
        assertThat(esc.getValue().escalatedBy()).isEqualTo(MEDIATOR.toString());

        StepVerifier.create(disputes.closeDispute(mediator(), d.getId().getValue(),
                new DisputeRequests.CloseDisputeRequest("RESOLVED_WITHOUT_COMPENSATION", "done", "someone-else")))
                .expectNextCount(1).verifyComplete();
        ArgumentCaptor<com.yowyob.tiibntick.core.dispute.application.command.CloseDisputeCommand> close =
                ArgumentCaptor.forClass(com.yowyob.tiibntick.core.dispute.application.command.CloseDisputeCommand.class);
        verify(commands).closeDispute(close.capture());
        assertThat(close.getValue().closedBy()).isEqualTo(MEDIATOR.toString());
    }

    @Test
    @DisplayName("by reference, a mediator sees internal notes and a party does not")
    void byReferenceRespectsPrivilege() {
        Dispute d = dispute();
        when(queries.getByReference(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(Mono.just(d));
        StepVerifier.create(disputes.getByReference(mediator(), "DSP-1"))
                .assertNext(r -> assertThat(r.comments()).hasSize(2)).verifyComplete();
        StepVerifier.create(disputes.getByReference(party(CLAIMANT), "DSP-1"))
                .assertNext(r -> assertThat(r.comments()).hasSize(1)).verifyComplete();
    }
}
