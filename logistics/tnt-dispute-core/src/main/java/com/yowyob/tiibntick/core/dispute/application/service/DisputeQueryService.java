package com.yowyob.tiibntick.core.dispute.application.service;

import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeQueryUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeRepository;
import com.yowyob.tiibntick.core.dispute.application.query.DisputePageResult;
import com.yowyob.tiibntick.core.dispute.application.query.GetDisputeQuery;
import com.yowyob.tiibntick.core.dispute.application.query.ListDisputesQuery;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeNotFoundException;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Application service implementing all dispute query (read) operations.
 * Read-only — never modifies the domain aggregate.
 *
 * @author MANFOUO Braun
 */
@Service
public class DisputeQueryService implements IDisputeQueryUseCase {

    private static final Logger log = LoggerFactory.getLogger(DisputeQueryService.class);

    private final IDisputeRepository repository;

    public DisputeQueryService(final IDisputeRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Mono<Dispute> getDispute(final GetDisputeQuery query) {
        Objects.requireNonNull(query, "GetDisputeQuery must not be null");
        log.debug("Fetching dispute id={} for tenant={}", query.disputeId(), query.tenantId());
        return repository.findByIdAndTenantId(query.disputeId(), query.tenantId())
                .switchIfEmpty(Mono.error(new DisputeNotFoundException(query.disputeId())));
    }

    @Override
    public Mono<DisputePageResult> listDisputes(final ListDisputesQuery query) {
        Objects.requireNonNull(query, "ListDisputesQuery must not be null");
        log.debug("Listing disputes for tenant={}, page={}, size={}", query.tenantId(), query.page(), query.size());

        final Mono<Long> totalCount = repository.countAll(query);
        final Flux<Dispute> disputes = repository.findAll(query);

        return Mono.zip(disputes.collectList(), totalCount)
                .map(tuple -> {
                    final long total = tuple.getT2();
                    final int totalPages = (int) Math.ceil((double) total / query.size());
                    return new DisputePageResult(tuple.getT1(), query.page(), query.size(), total, totalPages);
                });
    }

    @Override
    public Flux<Dispute> getDisputesByClaimant(final String claimantId, final String tenantId) {
        Objects.requireNonNull(claimantId, "claimantId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return repository.findActiveByClaimantId(claimantId, tenantId);
    }

    @Override
    public Flux<Dispute> getDisputesByRespondent(final String respondentId, final String tenantId) {
        Objects.requireNonNull(respondentId, "respondentId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return repository.findByRespondentId(respondentId, tenantId);
    }

    @Override
    public Mono<Dispute> getByReference(final String reference, final String tenantId) {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return repository.findByReferenceAndTenantId(reference, tenantId)
                .switchIfEmpty(Mono.error(new DisputeNotFoundException("Dispute not found with reference: " + reference)));
    }
    @Override
    public reactor.core.publisher.Flux<Dispute> findDisputesByFreelancerOrg(
            String freelancerOrgId, String status, String tenantId) {
        log.debug("Finding disputes for FreelancerOrg={} status={} tenant={}",
                freelancerOrgId, status, tenantId);
        return repository.findByFreelancerOrgId(freelancerOrgId, status, tenantId);
    }

    @Override
    public reactor.core.publisher.Mono<DisputeStats> getDisputeStatsByOrg(String freelancerOrgId, String tenantId) {
        log.debug("Computing dispute stats for FreelancerOrg={} tenant={}", freelancerOrgId, tenantId);
        return repository.findByFreelancerOrgId(freelancerOrgId, null, tenantId)
                .collectList()
                .map(disputes -> {
                    long total = disputes.size();
                    long open = disputes.stream().filter(d -> !d.getStatus().isTerminal()).count();
                    long resolved = disputes.stream()
                            .filter(d -> d.getStatus() == com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus.COMPENSATED
                                      || d.getStatus() == com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus.CLOSED_RESOLVED)
                            .count();
                    long withCompensation = disputes.stream()
                            .filter(d -> d.getCompensation() != null && d.getCompensation().getAmount() != null)
                            .count();
                    long withdrawn = disputes.stream()
                            .filter(d -> d.getStatus() == com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus.CLOSED_WITHDRAWN)
                            .count();
                    java.math.BigDecimal totalCompensation = disputes.stream()
                            .filter(d -> d.getCompensation() != null && d.getCompensation().getAmount() != null)
                            .map(d -> d.getCompensation().getAmount())
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    return new DisputeStats(
                            freelancerOrgId, "ALL", total, open, resolved,
                            withCompensation, withdrawn, totalCompensation,
                            total > 0 ? (double) total : 0.0,
                            total > 0 ? (double) withCompensation / total : 0.0);
                })
                .defaultIfEmpty(DisputeStats.empty(freelancerOrgId, "ALL"));
    }

}