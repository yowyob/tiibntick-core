package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity.PaymentSplitEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for PaymentSplitEntity.
 * @author MANFOUO Braun
 */
public interface PaymentSplitR2dbcRepository
        extends ReactiveCrudRepository<PaymentSplitEntity, UUID> {

    @Query("SELECT * FROM billing.wallet_payment_splits WHERE mission_id = :missionId ORDER BY created_at DESC")
    Flux<PaymentSplitEntity> findByMissionId(String missionId);

    @Query("SELECT * FROM billing.wallet_payment_splits WHERE freelancer_org_id = :orgId ORDER BY created_at DESC")
    Flux<PaymentSplitEntity> findByFreelancerOrgId(String orgId);
}
