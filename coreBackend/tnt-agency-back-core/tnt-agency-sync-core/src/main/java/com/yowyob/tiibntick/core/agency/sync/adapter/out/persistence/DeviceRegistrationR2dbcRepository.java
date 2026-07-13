package com.yowyob.tiibntick.core.agency.sync.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.sync.adapter.out.persistence.entity.DeviceRegistrationEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DeviceRegistrationR2dbcRepository
        extends ReactiveCrudRepository<DeviceRegistrationEntity, UUID> {

    Mono<DeviceRegistrationEntity> findByTenantIdAndAgencyIdAndUserIdAndDeviceId(
            UUID tenantId, UUID agencyId, UUID userId, String deviceId);
}
