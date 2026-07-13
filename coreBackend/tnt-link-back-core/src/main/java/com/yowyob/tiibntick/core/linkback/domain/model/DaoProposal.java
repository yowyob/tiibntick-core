package com.yowyob.tiibntick.core.linkback.domain.model;

import com.yowyob.tiibntick.core.linkback.domain.exception.DaoZoneDomainException;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * A governance proposal submitted to a {@link DaoZone} — members vote for or
 * against until the voting deadline, then the proposal is closed.
 *
 * @author Dilane PAFE
 */
@Getter
@Builder
public class DaoProposal {

    private final UUID id;
    private final UUID zoneId;
    private final UUID tenantId;
    private final String title;
    private final String description;
    private final UUID proposerId;
    private DaoProposalStatus status;
    private int votesFor;
    private int votesAgainst;
    private final Instant votingDeadline;
    private final Instant createdAt;
    private Instant updatedAt;

    public static DaoProposal propose(UUID zoneId, UUID tenantId, String title, String description,
                                       UUID proposerId, Instant votingDeadline) {
        if (title == null || title.isBlank()) {
            throw new DaoZoneDomainException("A proposal requires a title");
        }
        if (votingDeadline == null || !votingDeadline.isAfter(Instant.now())) {
            throw new DaoZoneDomainException("Voting deadline must be in the future");
        }
        Instant now = Instant.now();
        return DaoProposal.builder()
                .id(UUID.randomUUID())
                .zoneId(zoneId)
                .tenantId(tenantId)
                .title(title)
                .description(description)
                .proposerId(proposerId)
                .status(DaoProposalStatus.OPEN)
                .votesFor(0)
                .votesAgainst(0)
                .votingDeadline(votingDeadline)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /** Records a vote. Caller must have already enforced one-vote-per-member. */
    public void vote(boolean inFavor) {
        if (status != DaoProposalStatus.OPEN) {
            throw new DaoZoneDomainException("Cannot vote on a closed proposal: " + id);
        }
        if (Instant.now().isAfter(votingDeadline)) {
            throw new DaoZoneDomainException("Voting period has ended for proposal: " + id);
        }
        if (inFavor) {
            this.votesFor++;
        } else {
            this.votesAgainst++;
        }
        this.updatedAt = Instant.now();
    }

    public void close() {
        if (status != DaoProposalStatus.OPEN) {
            throw new DaoZoneDomainException("Proposal already closed: " + id);
        }
        this.status = votesFor > votesAgainst ? DaoProposalStatus.APPROVED : DaoProposalStatus.REJECTED;
        this.updatedAt = Instant.now();
    }
}
