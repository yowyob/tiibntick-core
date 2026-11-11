package com.yowyob.tiibntick.core.dispute.application.service;

import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeQueryUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeRepository;
import com.yowyob.tiibntick.core.dispute.application.query.DisputePageResult;
import com.yowyob.tiibntick.core.dispute.application.query.GetDisputeQuery;
import com.yowyob.tiibntick.core.dispute.application.query.ListDisputesQuery;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeNotFoundException;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeStats;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
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

    /**
     * A non-privileged caller (no {@code dispute:resolve}) may only see disputes where they
     * are the claimant or the respondent.
     */
    private boolean isVisible(Dispute dispute, String requesterId, boolean privileged) {
        if (privileged) {
            return true;
        }
        return requesterId != null
                && (requesterId.equals(dispute.getClaimantId()) || requesterId.equals(dispute.getRespondentId()));
    }

    @Override
    @RequirePermission(resource = "dispute", action = "read")
    public Mono<Dispute> getDispute(final GetDisputeQuery query) {
        Objects.requireNonNull(query, "GetDisputeQuery must not be null");
        log.debug("Fetching dispute id={} for tenant={}", query.disputeId(), query.tenantId());
        return repository.findByIdAndTenantId(query.disputeId(), query.tenantId())
                .switchIfEmpty(Mono.error(new DisputeNotFoundException(query.disputeId())))
                .filter(dispute -> isVisible(dispute, query.requesterId(), query.privileged()))
                .switchIfEmpty(Mono.error(new DisputeNotFoundException(query.disputeId())));
    }

    @Override
    @RequirePermission(resource = "dispute", action = "read")
    public Mono<DisputePageResult> listDisputes(final ListDisputesQuery query) {
        Objects.requireNonNull(query, "ListDisputesQuery must not be null");
        log.debug("Listing disputes for tenant={}, page={}, size={}", query.tenantId(), query.page(), query.size());

        if (!query.privileged()) {
            return listOwnDisputes(query);
        }
        return Mono.zip(repository.findAll(query).collectList(), repository.countAll(query))
                .map(tuple -> {
                    final long total = tuple.getT2();
                    final int totalPages = (int) Math.ceil((double) total / query.size());
                    return new DisputePageResult(tuple.getT1(), query.page(), query.size(), total, totalPages);
                });
    }

    /** A participant's most recent disputes considered per role (the repository caps a page at 100). */
    private static final int OWN_DISPUTES_LIMIT = 100;

    /**
     * A participant lists only the disputes they are a party of. The repository pages over the whole
     * tenant, so filtering its page afterwards would hand back arbitrary short or empty pages and
     * leak the tenant-wide count; instead their own disputes (as claimant, then as respondent) are
     * collected, merged, ordered newest first and paged here.
     */
    private Mono<DisputePageResult> listOwnDisputes(final ListDisputesQuery query) {
        final String me = query.requesterId();
        if (me == null) {
            return Mono.just(new DisputePageResult(java.util.List.of(), query.page(), query.size(), 0, 0));
        }
        Flux<Dispute> asClaimant = collectAll(query, me, null);
        Flux<Dispute> asRespondent = collectAll(query, null, me);
        return Flux.concat(asClaimant, asRespondent)
                .filter(d -> isVisible(d, me, false))
                .collectMap(d -> d.getId().getValue(), d -> d)
                .map(byId -> {
                    final java.util.List<Dispute> all = new java.util.ArrayList<>(byId.values());
                    all.sort(java.util.Comparator.comparing(Dispute::getFiledAt,
                            java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
                    final long total = all.size();
                    final int from = (int) Math.min((long) query.page() * query.size(), total);
                    final int to = (int) Math.min(from + (long) query.size(), total);
                    final int totalPages = (int) Math.ceil((double) total / query.size());
                    return new DisputePageResult(all.subList(from, to), query.page(), query.size(), total, totalPages);
                });
    }

    private Flux<Dispute> collectAll(final ListDisputesQuery base, final String claimant, final String respondent) {
        return repository.findAll(new ListDisputesQuery(
                base.tenantId(), base.requesterId(), base.statusFilter(), base.priorityFilter(),
                base.categoryFilter(), claimant, respondent, base.missionIdFilter(),
                base.from(), base.to(), 0, OWN_DISPUTES_LIMIT, false));
    }

    @Override
    @RequirePermission(resource = "dispute", action = "read")
    public Flux<Dispute> getDisputesByClaimant(final String claimantId, final String tenantId,
                                                final String requesterId, final boolean privileged) {
        Objects.requireNonNull(claimantId, "claimantId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return repository.findActiveByClaimantId(claimantId, tenantId)
                .filter(dispute -> isVisible(dispute, requesterId, privileged));
    }

    @Override
    public Flux<Dispute> getDisputesByRespondent(final String respondentId, final String tenantId) {
        Objects.requireNonNull(respondentId, "respondentId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return repository.findByRespondentId(respondentId, tenantId);
    }

    @Override
    @RequirePermission(resource = "dispute", action = "read")
    public Mono<Dispute> getByReference(final String reference, final String tenantId,
                                         final String requesterId, final boolean privileged) {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return repository.findByReferenceAndTenantId(reference, tenantId)
                .switchIfEmpty(Mono.error(new DisputeNotFoundException("Dispute not found with reference: " + reference)))
                .filter(dispute -> isVisible(dispute, requesterId, privileged))
                .switchIfEmpty(Mono.error(new DisputeNotFoundException("Dispute not found with reference: " + reference)));
    }
    @Override
    @RequirePermission(resource = "dispute", action = "read")
    public reactor.core.publisher.Flux<Dispute> findDisputesByFreelancerOrg(
            String freelancerOrgId, String status, String tenantId) {
        log.debug("Finding disputes for FreelancerOrg={} status={} tenant={}",
                freelancerOrgId, status, tenantId);
        return repository.findByFreelancerOrgId(freelancerOrgId, status, tenantId);
    }

    @Override
    @RequirePermission(resource = "dispute", action = "read")
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