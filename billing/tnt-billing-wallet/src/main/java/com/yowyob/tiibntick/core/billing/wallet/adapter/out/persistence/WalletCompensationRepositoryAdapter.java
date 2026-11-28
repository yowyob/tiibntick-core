package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity.WalletCompensationEntity;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository.WalletCompensationR2dbcRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IWalletCompensationRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletCompensation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class WalletCompensationRepositoryAdapter implements IWalletCompensationRepository {

    private final WalletCompensationR2dbcRepository repo;

    @Override
    public Mono<WalletCompensation> findByDisputeId(String disputeId) {
        return repo.findByDisputeId(disputeId).map(WalletCompensationRepositoryAdapter::toDomain);
    }

    @Override
    public Mono<WalletCompensation> insert(WalletCompensation c) {
        WalletCompensationEntity e = WalletCompensationEntity.builder()
                .id(c.id()).isNew(true).tenantId(c.tenantId()).disputeId(c.disputeId())
                .kernelCompensationId(c.kernelCompensationId()).fundWalletId(c.fundWalletId())
                .beneficiaryActorId(c.beneficiaryActorId()).beneficiaryWalletId(c.beneficiaryWalletId())
                .amount(c.amount()).currency(c.currency())
                .createdAt(LocalDateTime.ofInstant(c.createdAt(), ZoneOffset.UTC))
                .build();
        return repo.save(e).map(WalletCompensationRepositoryAdapter::toDomain);
    }

    private static WalletCompensation toDomain(WalletCompensationEntity e) {
        return new WalletCompensation(e.getId(), e.getTenantId(), e.getDisputeId(), e.getKernelCompensationId(),
                e.getFundWalletId(), e.getBeneficiaryActorId(), e.getBeneficiaryWalletId(), e.getAmount(),
                e.getCurrency(), e.getCreatedAt() == null ? null : e.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
