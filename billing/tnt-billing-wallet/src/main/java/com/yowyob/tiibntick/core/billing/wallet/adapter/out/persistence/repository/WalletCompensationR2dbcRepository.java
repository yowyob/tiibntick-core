package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity.WalletCompensationEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface WalletCompensationR2dbcRepository extends R2dbcRepository<WalletCompensationEntity, UUID> {

    Mono<WalletCompensationEntity> findByDisputeId(String disputeId);
}
