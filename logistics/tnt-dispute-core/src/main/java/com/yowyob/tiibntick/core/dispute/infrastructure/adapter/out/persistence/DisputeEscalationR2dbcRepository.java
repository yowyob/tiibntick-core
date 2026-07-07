package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence;

import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.entity.DisputeEscalationEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface DisputeEscalationR2dbcRepository extends ReactiveCrudRepository<DisputeEscalationEntity, String> {

    @Query("SELECT * FROM tnt_dispute_escalations WHERE dispute_id = :disputeId ORDER BY escalated_at ASC")
    Flux<DisputeEscalationEntity> findByDisputeId(String disputeId);
}
