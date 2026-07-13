package com.yowyob.tiibntick.core.linkback.application.service;

import com.yowyob.tiibntick.core.linkback.application.port.in.CloseDaoProposalUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.ProposeDaoProposalUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryDaoProposalsUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.VoteOnDaoProposalUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.command.ProposeDaoProposalCommand;
import com.yowyob.tiibntick.core.linkback.application.port.out.DaoProposalRepository;
import com.yowyob.tiibntick.core.linkback.application.port.out.DaoVoteRepository;
import com.yowyob.tiibntick.core.linkback.domain.exception.DaoZoneDomainException;
import com.yowyob.tiibntick.core.linkback.domain.model.DaoProposal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * @author Dilane PAFE
 */
@Service
@RequiredArgsConstructor
public class DaoProposalApplicationService implements
        ProposeDaoProposalUseCase, VoteOnDaoProposalUseCase, CloseDaoProposalUseCase, QueryDaoProposalsUseCase {

    private final DaoProposalRepository proposalRepository;
    private final DaoVoteRepository voteRepository;

    @Override
    public Mono<DaoProposal> propose(ProposeDaoProposalCommand command) {
        DaoProposal proposal = DaoProposal.propose(command.zoneId(), command.tenantId(), command.title(),
                command.description(), command.proposerId(), command.votingDeadline());
        return proposalRepository.save(proposal);
    }

    @Override
    public Mono<DaoProposal> vote(UUID tenantId, UUID proposalId, UUID voterId, boolean inFavor) {
        // hasVoted and the proposal lookup are independent reads — run them concurrently
        // instead of sequentially.
        return Mono.zip(voteRepository.hasVoted(proposalId, voterId), findOrError(tenantId, proposalId))
                .flatMap(tuple -> {
                    if (tuple.getT1()) {
                        return Mono.error(new DaoZoneDomainException(
                                "Actor " + voterId + " has already voted on proposal " + proposalId));
                    }
                    DaoProposal proposal = tuple.getT2();
                    proposal.vote(inFavor);
                    return voteRepository.recordVote(proposalId, voterId, inFavor)
                            .then(proposalRepository.save(proposal));
                });
    }

    @Override
    public Mono<DaoProposal> close(UUID tenantId, UUID proposalId) {
        return findOrError(tenantId, proposalId)
                .flatMap(proposal -> {
                    proposal.close();
                    return proposalRepository.save(proposal);
                });
    }

    @Override
    public Mono<DaoProposal> findById(UUID tenantId, UUID proposalId) {
        return proposalRepository.findById(tenantId, proposalId);
    }

    @Override
    public Flux<DaoProposal> findByZone(UUID tenantId, UUID zoneId) {
        return proposalRepository.findByZone(tenantId, zoneId);
    }

    @Override
    public Flux<DaoProposal> findOpenByZone(UUID tenantId, UUID zoneId) {
        return proposalRepository.findOpenByZone(tenantId, zoneId);
    }

    private Mono<DaoProposal> findOrError(UUID tenantId, UUID proposalId) {
        return proposalRepository.findById(tenantId, proposalId)
                .switchIfEmpty(Mono.error(new DaoZoneDomainException("DAO proposal not found: " + proposalId)));
    }
}
