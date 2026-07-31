package com.yowyob.tiibntick.core.dispute.application.service;

import com.yowyob.tiibntick.core.dispute.application.command.OpenDisputeCommand;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeRepository;
import com.yowyob.tiibntick.core.dispute.application.query.GetDisputeQuery;
import com.yowyob.tiibntick.core.dispute.domain.enums.*;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeNotFoundException;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DisputeQueryService}'s actor-level ownership scoping
 * (Go-Freelancer integration hardening, Audit n°7 · #6 follow-up).
 *
 * <p>A non-privileged caller (no {@code dispute:resolve}) must only ever see disputes
 * where they are the claimant or the respondent; a privileged caller sees everything.
 * {@code @RequirePermission} enforcement itself is proven separately in
 * {@link DisputePermissionEnforcementTest}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DisputeQueryService — ownership scoping")
class DisputeQueryServiceTest {

    @Mock private IDisputeRepository repository;

    private DisputeQueryService service;

    @BeforeEach
    void setUp() {
        service = new DisputeQueryService(repository);
    }

    private Dispute buildDispute(String claimantId, String respondentId) {
        OpenDisputeCommand cmd = new OpenDisputeCommand(
                "tenant-01", claimantId, ClaimantType.CLIENT, respondentId, RespondentType.FREELANCER,
                DisputeCause.PACKAGE_DAMAGED, DisputeCategory.MISSION_GO, DisputePriority.HIGH,
                "mission-001", "pkg-001", "TKG-001", "desc", null, null, false);
        return Dispute.open(cmd, DisputeReference.forSequence(1));
    }

    @Test
    @DisplayName("getDispute() - non-privileged claimant can read their own dispute")
    void getDispute_ownDispute_nonPrivileged_isVisible() {
        Dispute dispute = buildDispute("actor-self", "actor-other");
        when(repository.findByIdAndTenantId(any(), any())).thenReturn(Mono.just(dispute));

        GetDisputeQuery query = new GetDisputeQuery(dispute.getId(), "tenant-01", "actor-self", false);

        StepVerifier.create(service.getDispute(query))
                .expectNextMatches(d -> d.getClaimantId().equals("actor-self"))
                .verifyComplete();
    }

    @Test
    @DisplayName("getDispute() - non-privileged caller cannot read someone else's dispute")
    void getDispute_foreignDispute_nonPrivileged_isHidden() {
        Dispute dispute = buildDispute("actor-claimant", "actor-respondent");
        when(repository.findByIdAndTenantId(any(), any())).thenReturn(Mono.just(dispute));

        GetDisputeQuery query = new GetDisputeQuery(dispute.getId(), "tenant-01", "unrelated-actor", false);

        StepVerifier.create(service.getDispute(query))
                .expectError(DisputeNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("getDispute() - non-privileged respondent can read a dispute filed against them")
    void getDispute_asRespondent_nonPrivileged_isVisible() {
        Dispute dispute = buildDispute("actor-claimant", "actor-self");
        when(repository.findByIdAndTenantId(any(), any())).thenReturn(Mono.just(dispute));

        GetDisputeQuery query = new GetDisputeQuery(dispute.getId(), "tenant-01", "actor-self", false);

        StepVerifier.create(service.getDispute(query))
                .expectNextMatches(d -> d.getRespondentId().equals("actor-self"))
                .verifyComplete();
    }

    @Test
    @DisplayName("getDispute() - privileged caller (mediator/admin) can read any dispute")
    void getDispute_privilegedCaller_seesAnyDispute() {
        Dispute dispute = buildDispute("actor-claimant", "actor-respondent");
        when(repository.findByIdAndTenantId(any(), any())).thenReturn(Mono.just(dispute));

        GetDisputeQuery query = new GetDisputeQuery(dispute.getId(), "tenant-01", "mediator-1", true);

        StepVerifier.create(service.getDispute(query))
                .expectNextCount(1)
                .verifyComplete();
    }
}
