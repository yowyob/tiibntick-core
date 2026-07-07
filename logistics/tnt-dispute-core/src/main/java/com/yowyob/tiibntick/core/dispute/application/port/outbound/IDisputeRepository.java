package com.yowyob.tiibntick.core.dispute.application.port.outbound;

import com.yowyob.tiibntick.core.dispute.application.query.ListDisputesQuery;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Secondary port (outbound) for dispute persistence.
 * Implemented by {@code DisputeRepositoryAdapter} in the infrastructure layer.
 *
 * @author MANFOUO Braun
 */
public interface IDisputeRepository {

    /**
     * Saves a new or updated dispute aggregate.
     *
     * @param dispute the dispute to persist
     * @return the persisted dispute (with updated version)
     */
    Mono<Dispute> save(Dispute dispute);

    /**
     * Finds a dispute by its unique identifier within a tenant context.
     *
     * @param id       the dispute ID
     * @param tenantId the tenant scope
     * @return the dispute, or empty if not found
     */
    Mono<Dispute> findByIdAndTenantId(DisputeId id, String tenantId);

    /**
     * Finds a dispute by reference code (human-readable).
     *
     * @param reference the dispute reference, e.g. {@code DSP-202601-00042}
     * @param tenantId  the tenant scope
     * @return the dispute, or empty if not found
     */
    Mono<Dispute> findByReferenceAndTenantId(String reference, String tenantId);

    /**
     * Returns a paginated list of disputes matching the query filters.
     *
     * @param query the filter and pagination parameters
     * @return a Flux of matching disputes
     */
    Flux<Dispute> findAll(ListDisputesQuery query);

    /**
     * Counts the total number of disputes matching the query filters.
     *
     * @param query the filter parameters
     * @return the total count
     */
    Mono<Long> countAll(ListDisputesQuery query);

    /**
     * Returns all disputes in a given status that have exceeded their SLA deadline.
     * Used by the SLA scheduler to trigger automatic closure or escalation.
     *
     * @param status  the status to filter on
     * @param before  the deadline threshold — disputes with deadline before this time are returned
     * @return matching expired disputes
     */
    Flux<Dispute> findExpiredByStatusBefore(DisputeStatus status, LocalDateTime before);

    /**
     * Returns all open disputes for a specific claimant within a tenant.
     *
     * @param claimantId the claimant actor ID
     * @param tenantId   the tenant scope
     * @return the claimant's active disputes
     */
    Flux<Dispute> findActiveByClaimantId(String claimantId, String tenantId);

    /**
     * Returns all disputes involving a respondent (for reputation calculation).
     *
     * @param respondentId the respondent actor ID
     * @param tenantId     the tenant scope
     * @return matching disputes
     */
    Flux<Dispute> findByRespondentId(String respondentId, String tenantId);

    /**
     * Checks whether a dispute already exists for the given package ID.
     * Prevents duplicate disputes being opened for the same package.
     *
     * @param packageId the package ID
     * @param tenantId  the tenant scope
     * @return {@code true} if an active dispute exists
     */
    Mono<Boolean> existsActiveDisputeForPackage(String packageId, String tenantId);
    /**
     * Finds all disputes where the respondent org is the given FreelancerOrg ().
     *
     * @param freelancerOrgId the FreelancerOrg UUID
     * @param status          optional status filter (null = all statuses)
     * @param tenantId        tenant scope
     * @return Flux of matching disputes
     */
    reactor.core.publisher.Flux<Dispute> findByFreelancerOrgId(
            String freelancerOrgId, String status, String tenantId);

}