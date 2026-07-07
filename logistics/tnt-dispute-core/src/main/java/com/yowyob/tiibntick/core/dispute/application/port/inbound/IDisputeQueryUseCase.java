package com.yowyob.tiibntick.core.dispute.application.port.inbound;

import com.yowyob.tiibntick.core.dispute.application.query.DisputePageResult;
import com.yowyob.tiibntick.core.dispute.application.query.GetDisputeQuery;
import com.yowyob.tiibntick.core.dispute.application.query.ListDisputesQuery;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeStats;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Primary port (inbound) defining all read operations on disputes.
 * Implemented by {@code DisputeQueryService} in the application layer.
 *
 * @author MANFOUO Braun
 */
public interface IDisputeQueryUseCase {

    /**
     * Retrieves a single dispute by ID. Enforces tenant isolation.
     *
     * @param query the get query with tenant context
     * @return the dispute, or empty if not found or inaccessible
     */
    Mono<Dispute> getDispute(GetDisputeQuery query);

    /**
     * Returns a paginated, filtered list of disputes.
     *
     * @param query the list query with filters and pagination
     * @return a page of matching disputes
     */
    Mono<DisputePageResult> listDisputes(ListDisputesQuery query);

    /**
     * Returns all active disputes for a given claimant.
     *
     * @param claimantId the claimant actor ID
     * @param tenantId   the tenant scope
     * @return a Flux of active disputes
     */
    Flux<Dispute> getDisputesByClaimant(String claimantId, String tenantId);

    /**
     * Returns all disputes involving a given respondent (for reputation data).
     *
     * @param respondentId the respondent actor ID
     * @param tenantId     the tenant scope
     * @return a Flux of disputes
     */
    Flux<Dispute> getDisputesByRespondent(String respondentId, String tenantId);

    /**
     * Finds a dispute by its human-readable reference code.
     *
     * @param reference the reference string (e.g. {@code DSP-202601-00042})
     * @param tenantId  the tenant scope
     * @return the matching dispute, or empty
     */
    Mono<Dispute> getByReference(String reference, String tenantId);

    /**
     * Returns all disputes where the respondent is the given FreelancerOrg ().
     *
     * @param freelancerOrgId the FreelancerOrg UUID (from tnt-organization-core)
     * @param status          optional status filter (null = all statuses)
     * @param tenantId        tenant scope
     * @return Flux of disputes against this FreelancerOrg
     */
    Flux<Dispute> findDisputesByFreelancerOrg(String freelancerOrgId, String status, String tenantId);

    /**
     * Returns dispute statistics for a given FreelancerOrg ().
     * Used by admin dashboards and the org owner's dashboard.
     *
     * @param freelancerOrgId the FreelancerOrg UUID
     * @param tenantId        tenant scope
     * @return dispute stats summary
     */
    Mono<DisputeStats> getDisputeStatsByOrg(String freelancerOrgId, String tenantId);
}
