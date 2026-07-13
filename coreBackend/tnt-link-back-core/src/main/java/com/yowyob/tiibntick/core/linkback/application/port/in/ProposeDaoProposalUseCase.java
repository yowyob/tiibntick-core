package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.linkback.application.port.in.command.ProposeDaoProposalCommand;
import com.yowyob.tiibntick.core.linkback.domain.model.DaoProposal;
import reactor.core.publisher.Mono;

public interface ProposeDaoProposalUseCase {
    Mono<DaoProposal> propose(ProposeDaoProposalCommand command);
}
