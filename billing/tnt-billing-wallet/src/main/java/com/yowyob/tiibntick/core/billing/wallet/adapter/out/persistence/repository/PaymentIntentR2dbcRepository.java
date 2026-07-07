package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity.PaymentIntentEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Spring Data R2DBC repository for PaymentIntentEntity.
 * @author MANFOUO Braun
 */
@Repository
public interface PaymentIntentR2dbcRepository extends R2dbcRepository<PaymentIntentEntity, UUID> {
    Mono<PaymentIntentEntity> findByExternalRef(String externalRef);
    Mono<PaymentIntentEntity> findByIdempotencyKey(String idempotencyKey);
    Mono<PaymentIntentEntity> findByInvoiceId(String invoiceId);
}
