package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.mapper.WalletPersistenceMapper;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository.PaymentIntentR2dbcRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IPaymentIntentRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntent;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntentId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Adapter — implements IPaymentIntentRepository using Spring Data R2DBC.
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class PaymentIntentRepositoryAdapter implements IPaymentIntentRepository {

    private final PaymentIntentR2dbcRepository repo;
    private final WalletPersistenceMapper mapper;

    @Override
    public Mono<PaymentIntent> save(PaymentIntent intent) {
        return repo.existsById(intent.getId().value())
                .flatMap(exists -> {
                    var entity = mapper.toEntity(intent);
                    entity.setNew(!exists);
                    return repo.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Mono<PaymentIntent> findById(PaymentIntentId id) {
        return repo.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Mono<PaymentIntent> findByExternalRef(String externalRef) {
        return repo.findByExternalRef(externalRef).map(mapper::toDomain);
    }

    @Override
    public Mono<PaymentIntent> findByIdempotencyKey(String idempotencyKey) {
        return repo.findByIdempotencyKey(idempotencyKey).map(mapper::toDomain);
    }

    @Override
    public Mono<PaymentIntent> findByInvoiceId(String invoiceId) {
        return repo.findByInvoiceId(invoiceId).map(mapper::toDomain);
    }
}
