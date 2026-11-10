package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity.WalletSettlementEntity;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository.WalletSettlementR2dbcRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.ISettlementRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.CommissionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementMethod;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletSettlement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WalletSettlementRepositoryAdapter implements ISettlementRepository {

    private final WalletSettlementR2dbcRepository repo;

    @Override
    public Mono<WalletSettlement> save(WalletSettlement s) {
        WalletSettlementEntity e = toEntity(s);
        e.setNew(!s.isPersisted());
        return repo.save(e).map(WalletSettlementRepositoryAdapter::toDomain);
    }

    @Override
    public Mono<WalletSettlement> findById(UUID id) {
        return repo.findById(id).map(WalletSettlementRepositoryAdapter::toDomain);
    }

    @Override
    public Mono<WalletSettlement> findByReference(UUID tenantId, String referenceType, UUID referenceId) {
        return repo.findByTenantIdAndReferenceTypeAndReferenceId(tenantId, referenceType, referenceId)
                .map(WalletSettlementRepositoryAdapter::toDomain);
    }

    @Override
    public Mono<WalletSettlement> findByReference(String referenceType, UUID referenceId) {
        return repo.findFirstByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(referenceType, referenceId)
                .map(WalletSettlementRepositoryAdapter::toDomain);
    }

    @Override
    public Flux<WalletSettlement> findByTenantAndCommissionStatus(UUID tenantId, CommissionStatus status) {
        return repo.findByTenantIdAndCommissionStatusOrderByCreatedAtAsc(tenantId, status.name())
                .map(WalletSettlementRepositoryAdapter::toDomain);
    }

    private static WalletSettlementEntity toEntity(WalletSettlement s) {
        return WalletSettlementEntity.builder()
                .id(s.getId()).tenantId(s.getTenantId())
                .referenceType(s.getReferenceType()).referenceId(s.getReferenceId())
                .payerUserId(s.getPayerUserId()).payeeUserId(s.getPayeeUserId())
                .amount(s.getAmount()).currency(s.getCurrency())
                .method(s.getMethod().name()).status(s.getStatus().name())
                .kernelReference(s.getKernelReference())
                .commissionRate(s.getCommissionRate()).commissionAmount(s.getCommissionAmount())
                .commissionStatus(s.getCommissionStatus().name())
                .createdAt(s.getCreatedAt()).paidAt(s.getPaidAt()).updatedAt(s.getUpdatedAt())
                .build();
    }

    private static WalletSettlement toDomain(WalletSettlementEntity e) {
        return WalletSettlement.builder()
                .id(e.getId()).tenantId(e.getTenantId())
                .referenceType(e.getReferenceType()).referenceId(e.getReferenceId())
                .payerUserId(e.getPayerUserId()).payeeUserId(e.getPayeeUserId())
                .amount(e.getAmount()).currency(e.getCurrency())
                .method(SettlementMethod.valueOf(e.getMethod()))
                .status(SettlementStatus.valueOf(e.getStatus()))
                .kernelReference(e.getKernelReference())
                .commissionRate(e.getCommissionRate()).commissionAmount(e.getCommissionAmount())
                .commissionStatus(CommissionStatus.valueOf(e.getCommissionStatus()))
                .createdAt(e.getCreatedAt()).paidAt(e.getPaidAt()).updatedAt(e.getUpdatedAt())
                .persisted(true)
                .build();
    }
}
