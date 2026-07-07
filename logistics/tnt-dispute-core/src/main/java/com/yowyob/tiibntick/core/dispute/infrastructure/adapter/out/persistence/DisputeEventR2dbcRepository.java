package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence;

import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.entity.DisputeEventEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface DisputeEventR2dbcRepository extends ReactiveCrudRepository<DisputeEventEntity, String> {

    @Query("SELECT * FROM tnt_dispute_events WHERE dispute_id = :disputeId ORDER BY occurred_at ASC")
    Flux<DisputeEventEntity> findByDisputeId(String disputeId);

    @Query("SELECT * FROM tnt_dispute_events WHERE dispute_id = :disputeId AND tenant_id = :tenantId ORDER BY occurred_at ASC")
    Flux<DisputeEventEntity> findByDisputeIdAndTenantId(String disputeId, String tenantId);
}
