package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import com.yowyob.tiibntick.core.linkback.domain.model.DaoProposal;

public final class DaoProposalResponseMapper {

    private DaoProposalResponseMapper() {
    }

    public static DaoProposalResponse toResponse(DaoProposal proposal) {
        return new DaoProposalResponse(
                proposal.getId(),
                proposal.getZoneId(),
                proposal.getTitle(),
                proposal.getDescription(),
                proposal.getProposerId(),
                proposal.getStatus().name(),
                proposal.getVotesFor(),
                proposal.getVotesAgainst(),
                proposal.getVotingDeadline(),
                proposal.getCreatedAt(),
                proposal.getUpdatedAt()
        );
    }
}
