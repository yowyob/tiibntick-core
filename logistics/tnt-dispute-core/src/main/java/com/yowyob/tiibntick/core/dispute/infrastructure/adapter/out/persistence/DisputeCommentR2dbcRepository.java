package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence;

import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.entity.DisputeCommentEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface DisputeCommentR2dbcRepository extends ReactiveCrudRepository<DisputeCommentEntity, String> {

    @Query("SELECT * FROM tnt_dispute_comments WHERE dispute_id = :disputeId ORDER BY posted_at ASC")
    Flux<DisputeCommentEntity> findByDisputeId(String disputeId);

    @Query("SELECT * FROM tnt_dispute_comments WHERE dispute_id = :disputeId AND (is_internal = false OR :includeInternal = true) ORDER BY posted_at ASC")
    Flux<DisputeCommentEntity> findByDisputeIdAndVisibility(String disputeId, boolean includeInternal);
}
