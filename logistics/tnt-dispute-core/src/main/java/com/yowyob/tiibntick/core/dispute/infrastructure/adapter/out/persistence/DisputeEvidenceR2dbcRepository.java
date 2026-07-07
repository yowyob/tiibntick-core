package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence;

import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.entity.DisputeEvidenceEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Spring Data R2DBC repository for {@link DisputeEvidenceEntity}.
 *
 * @author MANFOUO Braun
 */
@Repository
public interface DisputeEvidenceR2dbcRepository extends ReactiveCrudRepository<DisputeEvidenceEntity, String> {

    Flux<DisputeEvidenceEntity> findByDisputeIdAndTenantId(String disputeId, String tenantId);

    Flux<DisputeEvidenceEntity> findByDisputeId(String disputeId);
}
