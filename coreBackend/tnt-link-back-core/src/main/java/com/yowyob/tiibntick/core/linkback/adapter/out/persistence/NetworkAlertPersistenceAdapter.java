package com.yowyob.tiibntick.core.linkback.adapter.out.persistence;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity.NetworkAlertEntity;
import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository.NetworkAlertR2dbcRepository;
import com.yowyob.tiibntick.core.linkback.application.port.out.NetworkAlertRepository;
import com.yowyob.tiibntick.core.linkback.domain.model.AlertSeverity;
import com.yowyob.tiibntick.core.linkback.domain.model.AlertStatus;
import com.yowyob.tiibntick.core.linkback.domain.model.AlertType;
import com.yowyob.tiibntick.core.linkback.domain.model.NetworkAlert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NetworkAlertPersistenceAdapter implements NetworkAlertRepository {

    private final NetworkAlertR2dbcRepository r2dbcRepository;

    @Override
    public Mono<NetworkAlert> save(NetworkAlert alert) {
        NetworkAlertEntity entity = toEntity(alert);
        return r2dbcRepository.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return r2dbcRepository.save(entity);
                })
                .map(this::toDomain);
    }

    @Override
    public Mono<NetworkAlert> findById(UUID tenantId, UUID alertId) {
        return r2dbcRepository.findByIdAndTenantId(alertId, tenantId).map(this::toDomain);
    }

    @Override
    public Flux<NetworkAlert> findActiveByTenant(UUID tenantId) {
        return r2dbcRepository.findByTenantIdAndStatus(tenantId, AlertStatus.ACTIVE.name()).map(this::toDomain);
    }

    @Override
    public Flux<NetworkAlert> findActiveWithinBoundingBox(UUID tenantId, double minLat, double maxLat, double minLng, double maxLng) {
        return r2dbcRepository.findByTenantIdAndStatusWithinBoundingBox(
                        tenantId, AlertStatus.ACTIVE.name(), minLat, maxLat, minLng, maxLng)
                .map(this::toDomain);
    }

    private NetworkAlertEntity toEntity(NetworkAlert alert) {
        return NetworkAlertEntity.builder()
                .id(alert.getId())
                .tenantId(alert.getTenantId())
                .reporterId(alert.getReporterId())
                .alertType(alert.getType().name())
                .description(alert.getDescription())
                .latitude(alert.getLocation().latitude())
                .longitude(alert.getLocation().longitude())
                .severity(alert.getSeverity().name())
                .status(alert.getStatus().name())
                .confirmCount(alert.getConfirmCount())
                .createdAt(alert.getCreatedAt())
                .updatedAt(alert.getUpdatedAt())
                .resolvedAt(alert.getResolvedAt())
                .build();
    }

    private NetworkAlert toDomain(NetworkAlertEntity entity) {
        return NetworkAlert.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .reporterId(entity.getReporterId())
                .type(AlertType.valueOf(entity.getAlertType()))
                .description(entity.getDescription())
                .location(GeoPoint.of(entity.getLatitude(), entity.getLongitude()))
                .severity(AlertSeverity.valueOf(entity.getSeverity()))
                .status(AlertStatus.valueOf(entity.getStatus()))
                .confirmCount(entity.getConfirmCount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .resolvedAt(entity.getResolvedAt())
                .build();
    }
}
