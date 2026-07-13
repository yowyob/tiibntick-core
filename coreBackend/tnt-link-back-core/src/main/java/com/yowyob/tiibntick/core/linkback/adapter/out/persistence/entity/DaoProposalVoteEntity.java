package com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/** One row per (proposal, voter) — enforces one-vote-per-member via a unique constraint. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "tnt_link", value = "dao_proposal_votes")
public class DaoProposalVoteEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("proposal_id")
    private UUID proposalId;

    @Column("voter_id")
    private UUID voterId;

    @Column("in_favor")
    private boolean inFavor;

    @Column("voted_at")
    private Instant votedAt;
}
