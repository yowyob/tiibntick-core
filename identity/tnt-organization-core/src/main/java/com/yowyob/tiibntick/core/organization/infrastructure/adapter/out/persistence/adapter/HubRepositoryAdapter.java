package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.organization.application.port.out.HubRepositoryPort;
import com.yowyob.tiibntick.core.organization.domain.model.HubRelais;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.entity.HubRelaisEntity;
import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.repository.HubRelaisR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Persistence adapter implementing {@link HubRepositoryPort} using Spring Data R2DBC.
 *
 * <p>Handles the bidirectional mapping between {@link HubRelais} domain aggregates and
 * {@link HubRelaisEntity} R2DBC entities. The PostGIS spatial query is delegated to
 * {@link HubRelaisR2dbcRepository#findOperationalHubsWithinPolygon(String)}.
 *
 * @author MANFOUO Braun
 */
@Component
public class HubRepositoryAdapter implements HubRepositoryPort {

    private final HubRelaisR2dbcRepository repository;

    /**
     * Constructor injection.
     *
     * @param repository the Spring Data R2DBC repository
     */
    public HubRepositoryAdapter(HubRelaisR2dbcRepository repository) {
        this.repository = repository;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<HubRelais> save(HubRelais hub) {
        return repository.existsById(hub.getId().value())
                .flatMap(exists -> {
                    var entity = toEntity(hub);
                    entity.setNew(!exists);
                    return repository.save(entity);
                })
                .map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<HubRelais> findById(OrganizationId id) {
        return repository.findById(id.value()).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Flux<HubRelais> findWithinPolygon(String polygonWkt) {
        return repository.findOperationalHubsWithinPolygon(polygonWkt).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Flux<HubRelais> findOperationalByOrganizationId(UUID organizationId) {
        return repository.findByOrganizationIdAndOperational(organizationId, true)
                .map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Flux<HubRelais> findAllByTenantId(UUID tenantId) {
        return repository.findByTenantId(tenantId).map(this::toDomain);
    }

    // ─── Mapping helpers ─────────────────────────────────────────────────────

    /**
     * Maps a {@link HubRelais} domain aggregate to a {@link HubRelaisEntity} for persistence.
     *
     * @param hub the domain aggregate
     * @return the corresponding R2DBC entity
     */
    private HubRelaisEntity toEntity(HubRelais hub) {
        return HubRelaisEntity.builder()
                .id(hub.getId().value())
                .organizationId(hub.getOrganizationId())
                .tenantId(hub.getTenantId())
                .name(hub.getName())
                .maxParcelCapacity(hub.getMaxParcelCapacity())
                .geographicPointWkt(hub.getGeographicPointWkt())
                .openingHours(hub.getOpeningHours())
                .operatorId(hub.getOperatorId())
                .operational(hub.isOperational())
                .createdAt(hub.getCreatedAt())
                .updatedAt(hub.getUpdatedAt())
                .build();
    }

    /**
     * Maps a {@link HubRelaisEntity} to a {@link HubRelais} domain aggregate.
     *
     * @param entity the R2DBC entity from the database
     * @return the reconstituted domain aggregate
     */
    private HubRelais toDomain(HubRelaisEntity entity) {
        return new HubRelais(
                OrganizationId.of(entity.getId()),
                entity.getOrganizationId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getMaxParcelCapacity(),
                entity.getGeographicPointWkt(),
                entity.getOpeningHours(),
                entity.getOperatorId(),
                entity.isOperational(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
