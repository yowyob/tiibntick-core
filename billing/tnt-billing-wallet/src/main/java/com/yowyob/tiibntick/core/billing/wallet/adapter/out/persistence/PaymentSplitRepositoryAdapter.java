package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity.PaymentSplitEntity;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository.PaymentSplitR2dbcRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IPaymentSplitRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.TransactionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentSplit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Driven adapter implementing IPaymentSplitRepository via Spring Data R2DBC.
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class PaymentSplitRepositoryAdapter implements IPaymentSplitRepository {

    private final PaymentSplitR2dbcRepository r2dbcRepo;

    @Override
    public Mono<PaymentSplit> save(PaymentSplit split) {
        PaymentSplitEntity entity = PaymentSplitEntity.builder()
                .id(split.getId())
                .missionId(split.getMissionId())
                .totalAmount(split.getTotalAmount())
                .currency(split.getCurrency())
                .platformCommission(split.getPlatformCommission())
                .orgRevenue(split.getOrgRevenue())
                .freelancerOrgId(null) // set via mission context
                .subDelivererCommission(split.getSubDelivererCommission())
                .subDelivererId(split.getSubDelivererId())
                .status(split.getStatus().name())
                .executedAt(split.getExecutedAt())
                .createdAt(split.getCreatedAt())
                .build();
        return r2dbcRepo.existsById(split.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return r2dbcRepo.save(entity);
                })
                .map(this::toDomain);
    }

    @Override
    public Mono<PaymentSplit> findById(UUID id) {
        return r2dbcRepo.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<PaymentSplit> findByMissionId(String missionId) {
        return r2dbcRepo.findByMissionId(missionId).map(this::toDomain);
    }

    @Override
    public Flux<PaymentSplit> findByFreelancerOrgId(String freelancerOrgId) {
        return r2dbcRepo.findByFreelancerOrgId(freelancerOrgId).map(this::toDomain);
    }

    private PaymentSplit toDomain(PaymentSplitEntity e) {
        return PaymentSplit.builder()
                .id(e.getId())
                .missionId(e.getMissionId())
                .totalAmount(e.getTotalAmount())
                .currency(e.getCurrency())
                .platformCommission(e.getPlatformCommission())
                .orgRevenue(e.getOrgRevenue())
                .subDelivererCommission(e.getSubDelivererCommission())
                .subDelivererId(e.getSubDelivererId())
                .status(TransactionStatus.valueOf(e.getStatus()))
                .executedAt(e.getExecutedAt())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
