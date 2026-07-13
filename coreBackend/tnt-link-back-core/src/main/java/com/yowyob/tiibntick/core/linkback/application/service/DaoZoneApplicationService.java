package com.yowyob.tiibntick.core.linkback.application.service;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.application.port.in.ArchiveDaoZoneUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.CreateDaoZoneUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryDaoZonesUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.command.CreateDaoZoneCommand;
import com.yowyob.tiibntick.core.linkback.application.port.out.DaoZoneRepository;
import com.yowyob.tiibntick.core.linkback.domain.exception.DaoZoneDomainException;
import com.yowyob.tiibntick.core.linkback.domain.model.DaoZone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Genuinely new Link business logic — community-governance zones with no
 * equivalent in L2-L5.
 *
 * @author Dilane PAFE
 */
@Service
@RequiredArgsConstructor
public class DaoZoneApplicationService implements
        CreateDaoZoneUseCase, ArchiveDaoZoneUseCase, QueryDaoZonesUseCase {

    private final DaoZoneRepository repository;

    @Override
    public Mono<DaoZone> create(CreateDaoZoneCommand command) {
        DaoZone zone = DaoZone.create(command.tenantId(), command.name(), command.description(),
                command.center(), command.radiusKm(), command.createdBy());
        return repository.save(zone);
    }

    @Override
    public Mono<DaoZone> archive(UUID tenantId, UUID zoneId) {
        return findOrError(tenantId, zoneId)
                .flatMap(zone -> {
                    zone.archive();
                    return repository.save(zone);
                });
    }

    @Override
    public Mono<DaoZone> findById(UUID tenantId, UUID zoneId) {
        return repository.findById(tenantId, zoneId);
    }

    @Override
    public Flux<DaoZone> findActiveByTenant(UUID tenantId) {
        return repository.findActiveByTenant(tenantId);
    }

    @Override
    public Flux<DaoZone> findContaining(UUID tenantId, GeoPoint point) {
        return repository.findActiveContaining(tenantId, point.latitude(), point.longitude())
                .filter(zone -> zone.contains(point));
    }

    private Mono<DaoZone> findOrError(UUID tenantId, UUID zoneId) {
        return repository.findById(tenantId, zoneId)
                .switchIfEmpty(Mono.error(new DaoZoneDomainException("DAO zone not found: " + zoneId)));
    }
}
