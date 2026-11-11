package com.yowyob.tiibntick.core.dispute.application.service;

import com.yowyob.tiibntick.core.dispute.application.command.OpenDisputeCommand;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeRepository;
import com.yowyob.tiibntick.core.dispute.application.query.ListDisputesQuery;
import com.yowyob.tiibntick.core.dispute.domain.enums.*;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The repository pages over the whole tenant. A participant's listing must therefore be assembled
 * from their own disputes, not cut out of a tenant page (which gave arbitrary short pages and leaked
 * the tenant-wide total).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Dispute listing is scoped to the caller's own disputes")
class DisputeListingScopeTest {

    @Mock private IDisputeRepository repository;
    private DisputeQueryService service;
    private int sequence = 0;

    @BeforeEach
    void setUp() {
        service = new DisputeQueryService(repository);
    }

    private Dispute dispute(String claimant, String respondent) {
        OpenDisputeCommand cmd = new OpenDisputeCommand(
                "tenant-01", claimant, ClaimantType.CLIENT, respondent, RespondentType.FREELANCER,
                DisputeCause.PACKAGE_DAMAGED, DisputeCategory.MISSION_GO, DisputePriority.HIGH,
                "mission-" + sequence, "pkg-" + sequence, "TKG-" + sequence, "desc", null, null, false);
        return Dispute.open(cmd, DisputeReference.forSequence(++sequence));
    }

    private ListDisputesQuery query(String requester, boolean privileged, int page, int size) {
        return new ListDisputesQuery("tenant-01", requester, null, null, null, null, null, null,
                null, null, page, size, privileged);
    }

    @Test
    @DisplayName("a participant gets their disputes as claimant and as respondent, merged, with a total that is theirs only")
    void participantSeesOwnDisputesOnly() {
        Dispute mine1 = dispute("me", "driver");
        Dispute mine2 = dispute("me", "driver");
        Dispute asRespondent = dispute("someone", "me");
        lenient().when(repository.findAll(any())).thenAnswer(inv -> {
            ListDisputesQuery q = inv.getArgument(0);
            if ("me".equals(q.claimantIdFilter())) return Flux.just(mine1, mine2);
            if ("me".equals(q.respondentIdFilter())) return Flux.just(asRespondent);
            // an unscoped (tenant) query would return strangers' disputes too
            return Flux.just(mine1, dispute("a", "b"), dispute("c", "d"));
        });

        StepVerifier.create(service.listDisputes(query("me", false, 0, 20)))
                .assertNext(page -> {
                    assertThat(page.content()).hasSize(3).containsExactlyInAnyOrder(mine1, mine2, asRespondent);
                    assertThat(page.totalElements()).isEqualTo(3);
                })
                .verifyComplete();
        verify(repository, never()).countAll(any());
    }

    @Test
    @DisplayName("paging applies to the participant's own list")
    void pagingOverOwnList() {
        Dispute d1 = dispute("me", "x");
        Dispute d2 = dispute("me", "x");
        Dispute d3 = dispute("me", "x");
        lenient().when(repository.findAll(any())).thenAnswer(inv ->
                "me".equals(((ListDisputesQuery) inv.getArgument(0)).claimantIdFilter())
                        ? Flux.just(d1, d2, d3) : Flux.empty());

        StepVerifier.create(service.listDisputes(query("me", false, 1, 2)))
                .assertNext(page -> {
                    assertThat(page.content()).hasSize(1);
                    assertThat(page.totalElements()).isEqualTo(3);
                    assertThat(page.totalPages()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("a caller without identity gets nothing")
    void anonymousGetsNothing() {
        StepVerifier.create(service.listDisputes(query(null, false, 0, 20)))
                .assertNext(page -> assertThat(page.content()).isEmpty())
                .verifyComplete();
        verify(repository, never()).findAll(any());
    }

    @Test
    @DisplayName("a mediator still lists the whole tenant through the repository's own paging and count")
    void mediatorSeesTenant() {
        Dispute d = dispute("a", "b");
        lenient().when(repository.findAll(any())).thenReturn(Flux.just(d));
        lenient().when(repository.countAll(any())).thenReturn(Mono.just(42L));
        StepVerifier.create(service.listDisputes(query("mediator", true, 0, 20)))
                .assertNext(page -> {
                    assertThat(page.content()).containsExactly(d);
                    assertThat(page.totalElements()).isEqualTo(42);
                })
                .verifyComplete();
    }
}
