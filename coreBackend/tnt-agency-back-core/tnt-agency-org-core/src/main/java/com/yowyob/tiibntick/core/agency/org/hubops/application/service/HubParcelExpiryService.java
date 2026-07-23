package com.yowyob.tiibntick.core.agency.org.hubops.application.service;

import com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence.HubParcelRecordR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.hubops.application.mapper.HubParcelMapper;
import com.yowyob.tiibntick.core.agency.org.hubops.domain.HubParcelRecord;
import com.yowyob.tiibntick.core.agency.org.hubops.domain.vo.ParcelStatus;
import com.yowyob.tiibntick.core.agency.eventing.application.port.AgencyEventPublisher;
import com.yowyob.tiibntick.core.agency.eventing.domain.event.HubParcelExpired;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HubParcelExpiryService {

    private final HubParcelRecordR2dbcRepository parcelRepo;
    private final HubOccupancyService occupancyService;
    private final AgencyEventPublisher eventPublisher;

    /** Process expired parcels for one tenant (manual API). */
    @Transactional
    public Mono<Integer> processExpired(UUID tenantId) {
        Instant now = Instant.now();
        return parcelRepo.findByTenantIdAndStatusAndWithdrawalDeadlineBefore(
                        tenantId, ParcelStatus.DEPOSITED.name(), now)
                .map(HubParcelMapper::toDomain)
                .flatMap(record -> expireOne(record, now))
                .count()
                .map(Long::intValue);
    }

    /** Process expired parcels across all tenants (scheduled job). */
    @Transactional
    public Mono<Integer> processExpiredAllTenants() {
        Instant now = Instant.now();
        return parcelRepo.findByStatusAndWithdrawalDeadlineBefore(ParcelStatus.DEPOSITED.name(), now)
                .map(HubParcelMapper::toDomain)
                .flatMap(record -> expireOne(record, now))
                .count()
                .map(Long::intValue);
    }

    private Mono<HubParcelRecord> expireOne(HubParcelRecord record, Instant now) {
        record.markExpired(now);
        return parcelRepo.save(HubParcelMapper.toEntity(record))
                .map(HubParcelMapper::toDomain)
                .flatMap(saved -> occupancyService.adjustOccupancy(
                                saved.getTenantId(), saved.getHubId(), -1)
                        .thenReturn(saved))
                .flatMap(saved -> eventPublisher.publish(new HubParcelExpired(
                                UUID.randomUUID(), saved.getId(), saved.getTenantId(),
                                saved.getHubId(), saved.getPackageId(), saved.getTrackingCode(), now))
                        .thenReturn(saved));
    }
}
