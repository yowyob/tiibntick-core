package com.yowyob.tiibntick.core.agency.fleet.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.fleet.adapter.out.persistence.entity.VehicleEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AgencyVehicleR2dbcRepository extends ReactiveCrudRepository<VehicleEntity, UUID> {

    Mono<VehicleEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Flux<VehicleEntity> findByAgencyIdAndTenantId(UUID agencyId, UUID tenantId);

    @Query("""
            SELECT COUNT(*) > 0 FROM agency_fleet.vehicles
            WHERE license_plate = :licensePlate AND tenant_id = :tenantId
            """)
    Mono<Boolean> existsByLicensePlateAndTenantId(String licensePlate, UUID tenantId);
}
