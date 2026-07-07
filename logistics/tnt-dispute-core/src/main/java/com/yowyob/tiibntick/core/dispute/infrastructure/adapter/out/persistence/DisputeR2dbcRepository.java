package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence;

import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.entity.DisputeEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Spring Data R2DBC repository for {@link DisputeEntity}.
 * Provides reactive database access for dispute records.
 *
 * @author MANFOUO Braun
 */
@Repository
public interface DisputeR2dbcRepository extends ReactiveCrudRepository<DisputeEntity, String> {

    Mono<DisputeEntity> findByIdAndTenantId(String id, String tenantId);

    Mono<DisputeEntity> findByReferenceAndTenantId(String reference, String tenantId);

    Flux<DisputeEntity> findByTenantIdAndStatus(String tenantId, String status);

    Flux<DisputeEntity> findByClaimantIdAndTenantIdAndStatusNot(String claimantId, String tenantId, String status);

    Flux<DisputeEntity> findByRespondentIdAndTenantId(String respondentId, String tenantId);

    @Query("""
            SELECT * FROM tnt_disputes
            WHERE status = :status
              AND deadline < :before
              AND tenant_id IS NOT NULL
            """)
    Flux<DisputeEntity> findExpiredByStatusBefore(String status, LocalDateTime before);

    @Query("SELECT COALESCE(MAX(CAST(SPLIT_PART(reference, '-', 3) AS INTEGER)), 0) FROM tnt_disputes")
    Mono<Integer> findMaxReferenceSequence();

    @Query("""
            SELECT COUNT(*) > 0 FROM tnt_disputes
            WHERE package_id = :packageId
              AND tenant_id = :tenantId
              AND status NOT IN ('COMPENSATED', 'CLOSED_RESOLVED', 'CLOSED_WITHDRAWN', 'CLOSED_EXPIRED')
            """)
    Mono<Boolean> existsActiveDisputeForPackage(String packageId, String tenantId);

    @Query("""
            SELECT * FROM tnt_disputes
            WHERE tenant_id = :tenantId
              AND (:status IS NULL OR status = :status)
              AND (:priority IS NULL OR priority = :priority)
              AND (:category IS NULL OR category = :category)
              AND (:claimantId IS NULL OR claimant_id = :claimantId)
              AND (:respondentId IS NULL OR respondent_id = :respondentId)
              AND (:missionId IS NULL OR mission_id = :missionId)
              AND (:from IS NULL OR filed_at >= :from)
              AND (:to IS NULL OR filed_at <= :to)
            ORDER BY filed_at DESC
            LIMIT :size OFFSET :offset
            """)
    Flux<DisputeEntity> findAllFiltered(
            String tenantId,
            String status,
            String priority,
            String category,
            String claimantId,
            String respondentId,
            String missionId,
            LocalDateTime from,
            LocalDateTime to,
            int size,
            long offset);

    @Query("""
            SELECT COUNT(*) FROM tnt_disputes
            WHERE tenant_id = :tenantId
              AND (:status IS NULL OR status = :status)
              AND (:priority IS NULL OR priority = :priority)
              AND (:category IS NULL OR category = :category)
              AND (:claimantId IS NULL OR claimant_id = :claimantId)
              AND (:respondentId IS NULL OR respondent_id = :respondentId)
            """)
    Mono<Long> countFiltered(
            String tenantId,
            String status,
            String priority,
            String category,
            String claimantId,
            String respondentId);
    /**
     * Finds all disputes where the respondent org is the given FreelancerOrg ().
     */
    @Query("""
            SELECT * FROM tnt_disputes
            WHERE respondent_org_id = :freelancerOrgId
              AND tenant_id = :tenantId
              AND (:status IS NULL OR status = :status)
            ORDER BY filed_at DESC
            """)
    Flux<DisputeEntity> findByRespondentOrgIdAndTenantId(
            String freelancerOrgId, String tenantId, String status);

}