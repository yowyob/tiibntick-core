package com.yowyob.tiibntick.core.geo.application.service;

import com.yowyob.tiibntick.core.geo.application.port.in.IGeofenceUseCase;
import com.yowyob.tiibntick.core.geo.application.port.out.IGeoEventPublisher;
import com.yowyob.tiibntick.core.geo.application.port.out.IServiceZoneRepository;
import com.yowyob.tiibntick.core.geo.domain.event.ServiceZoneUpdatedEvent;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.ServiceZonePolygon;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Application service for geographic service zone (geofencing) operations.
 * Uses the ray-casting algorithm via {@link ServiceZonePolygon#contains(GeoPoint)}.
 *
 * Author: MANFOUO Braun
 */
@Service
public class GeofencingService implements IGeofenceUseCase {

    private final IServiceZoneRepository zoneRepository;
    private final IGeoEventPublisher eventPublisher;

    public GeofencingService(IServiceZoneRepository zoneRepository,
                             IGeoEventPublisher eventPublisher) {
        this.zoneRepository = zoneRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Mono<Boolean> isPointInZone(GeoPoint point, UUID zoneId, UUID tenantId) {
        return zoneRepository.findById(zoneId, tenantId)
                .switchIfEmpty(Mono.error(new GeoNotFoundException("ServiceZone", zoneId.toString())))
                .map(zone -> zone.isActive() && zone.contains(point));
    }

    @Override
    public Mono<Boolean> isPointCoveredByAgency(GeoPoint point, UUID agencyId, UUID tenantId) {
        return zoneRepository.findByAgency(agencyId, tenantId)
                .filter(ServiceZonePolygon::isActive)
                .any(zone -> zone.contains(point));
    }

    @Override
    public Mono<UUID> findContainingZone(GeoPoint point, UUID tenantId) {
        return zoneRepository.findAllActiveByTenant(tenantId)
                .filter(zone -> zone.contains(point))
                .next()
                .map(ServiceZonePolygon::id);
    }

    @Transactional
    public Mono<ServiceZonePolygon> createZone(UUID tenantId, UUID agencyId,
                                                String name, List<GeoPoint> vertices) {
        ServiceZonePolygon zone = ServiceZonePolygon.create(tenantId, agencyId, name, vertices);
        return zoneRepository.save(zone)
                .flatMap(saved -> {
                    ServiceZoneUpdatedEvent event =
                            ServiceZoneUpdatedEvent.created(tenantId, saved.id(), agencyId, name);
                    return eventPublisher.publishServiceZoneUpdated(event).thenReturn(saved);
                });
    }

    public Flux<ServiceZonePolygon> findZonesByAgency(UUID agencyId, UUID tenantId) {
        return zoneRepository.findByAgency(agencyId, tenantId);
    }
}
