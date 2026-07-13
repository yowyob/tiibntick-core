package com.yowyob.tiibntick.core.linkback.adapter.out.persistence;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity.DaoZoneEntity;
import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository.DaoZoneR2dbcRepository;
import com.yowyob.tiibntick.core.linkback.application.port.out.DaoZoneRepository;
import com.yowyob.tiibntick.core.linkback.domain.model.DaoZone;
import com.yowyob.tiibntick.core.linkback.domain.model.DaoZoneStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DaoZonePersistenceAdapter implements DaoZoneRepository {

    private final DaoZoneR2dbcRepository r2dbcRepository;

    @Override
    public Mono<DaoZone> save(DaoZone zone) {
        DaoZoneEntity entity = toEntity(zone);
        return r2dbcRepository.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return r2dbcRepository.save(entity);
                })
                .map(this::toDomain);
    }

    @Override
    public Mono<DaoZone> findById(UUID tenantId, UUID zoneId) {
        return r2dbcRepository.findByIdAndTenantId(zoneId, tenantId).map(this::toDomain);
    }

    @Override
    public Flux<DaoZone> findActiveByTenant(UUID tenantId) {
        return r2dbcRepository.findByTenantIdAndStatus(tenantId, DaoZoneStatus.ACTIVE.name()).map(this::toDomain);
    }

    @Override
    public Flux<DaoZone> findActiveContaining(UUID tenantId, double lat, double lng) {
        return r2dbcRepository.findByTenantIdAndStatusContainingPoint(tenantId, DaoZoneStatus.ACTIVE.name(), lat, lng)
                .map(this::toDomain);
    }

    private DaoZoneEntity toEntity(DaoZone zone) {
        return DaoZoneEntity.builder()
                .id(zone.getId())
                .tenantId(zone.getTenantId())
                .name(zone.getName())
                .description(zone.getDescription())
                .centerLatitude(zone.getCenter().latitude())
                .centerLongitude(zone.getCenter().longitude())
                .radiusKm(zone.getRadiusKm())
                .status(zone.getStatus().name())
                .createdBy(zone.getCreatedBy())
                .createdAt(zone.getCreatedAt())
                .updatedAt(zone.getUpdatedAt())
                .build();
    }

    private DaoZone toDomain(DaoZoneEntity entity) {
        return DaoZone.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .name(entity.getName())
                .description(entity.getDescription())
                .center(GeoPoint.of(entity.getCenterLatitude(), entity.getCenterLongitude()))
                .radiusKm(entity.getRadiusKm())
                .status(DaoZoneStatus.valueOf(entity.getStatus()))
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
