package com.yowyob.tiibntick.core.tp.application.port.out;

import com.yowyob.tiibntick.core.tp.domain.model.KycRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Output port: persistence for KycRecord.
 *
 * @author MANFOUO Braun
 */
public interface KycRecordRepository {

    Mono<KycRecord> save(KycRecord kycRecord);

    Mono<KycRecord> findById(UUID kycRecordId);

    Mono<KycRecord> findLatestByThirdPartyId(UUID tenantId, UUID thirdPartyId);

    Flux<KycRecord> findAllByThirdPartyId(UUID tenantId, UUID thirdPartyId);

    Mono<Boolean> existsPendingByThirdPartyId(UUID tenantId, UUID thirdPartyId);
}
