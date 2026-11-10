package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity.WalletHoldEntity;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository.WalletHoldR2dbcRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IWalletHoldRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.HoldStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletHold;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WalletHoldRepositoryAdapter implements IWalletHoldRepository {

    private final WalletHoldR2dbcRepository repo;

    @Override
    public Mono<WalletHold> save(WalletHold h) {
        WalletHoldEntity e = WalletHoldEntity.builder()
                .id(h.getId()).tenantId(h.getTenantId()).ownerUserId(h.getOwnerUserId())
                .referenceType(h.getReferenceType()).referenceId(h.getReferenceId())
                .amount(h.getAmount()).currency(h.getCurrency()).reason(h.getReason())
                .status(h.getStatus().name()).createdAt(h.getCreatedAt()).releasedAt(h.getReleasedAt())
                .build();
        e.setNew(!h.isPersisted());
        return repo.save(e).map(WalletHoldRepositoryAdapter::toDomain);
    }

    @Override
    public Flux<WalletHold> findActiveByOwner(UUID ownerUserId) {
        return repo.findByOwnerUserIdAndStatus(ownerUserId, HoldStatus.ACTIVE.name())
                .map(WalletHoldRepositoryAdapter::toDomain);
    }

    @Override
    public Flux<WalletHold> findActiveByReference(String referenceType, UUID referenceId) {
        return repo.findByReferenceTypeAndReferenceIdAndStatus(referenceType, referenceId, HoldStatus.ACTIVE.name())
                .map(WalletHoldRepositoryAdapter::toDomain);
    }

    private static WalletHold toDomain(WalletHoldEntity e) {
        return WalletHold.builder()
                .id(e.getId()).tenantId(e.getTenantId()).ownerUserId(e.getOwnerUserId())
                .referenceType(e.getReferenceType()).referenceId(e.getReferenceId())
                .amount(e.getAmount()).currency(e.getCurrency()).reason(e.getReason())
                .status(HoldStatus.valueOf(e.getStatus())).createdAt(e.getCreatedAt())
                .releasedAt(e.getReleasedAt()).persisted(true)
                .build();
    }
}
