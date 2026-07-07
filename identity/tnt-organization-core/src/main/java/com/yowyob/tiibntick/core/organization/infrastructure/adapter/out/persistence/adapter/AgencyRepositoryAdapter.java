package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.organization.application.port.out.AgencyRepositoryPort;
import com.yowyob.tiibntick.core.organization.domain.model.Agency;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.entity.AgencyEntity;
import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.repository.AgencyR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Persistence adapter implementing {@link AgencyRepositoryPort} using Spring Data R2DBC.
 *
 * <p>Handles the bidirectional mapping between {@link Agency} domain aggregates and
 * {@link AgencyEntity} R2DBC entities. All operations are fully reactive (Reactor).
 *
 * @author MANFOUO Braun
 */
@Component
public class AgencyRepositoryAdapter implements AgencyRepositoryPort {

    private final AgencyR2dbcRepository repository;

    /**
     * Constructor injection.
     *
     * @param repository the Spring Data R2DBC repository
     */
    public AgencyRepositoryAdapter(AgencyR2dbcRepository repository) {
        this.repository = repository;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Agency> save(Agency agency) {
        return repository.existsById(agency.getId().value())
                .flatMap(exists -> {
                    var entity = toEntity(agency);
                    entity.setNew(!exists);
                    return repository.save(entity);
                })
                .map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Agency> findById(OrganizationId id) {
        return repository.findById(id.value()).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Flux<Agency> findByOrganizationId(UUID organizationId) {
        return repository.findByOrganizationId(organizationId).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Flux<Agency> findAllByTenantId(UUID tenantId) {
        return repository.findByTenantId(tenantId).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Boolean> existsById(OrganizationId id) {
        return repository.existsById(id.value());
    }

    // ─── Mapping helpers ─────────────────────────────────────────────────────

    /**
     * Maps an {@link Agency} domain aggregate to an {@link AgencyEntity} for persistence.
     *
     * @param agency the domain aggregate
     * @return the corresponding R2DBC entity
     */
    private AgencyEntity toEntity(Agency agency) {
        return AgencyEntity.builder()
                .id(agency.getId().value())
                .organizationId(agency.getOrganizationId())
                .tenantId(agency.getTenantId())
                .name(agency.getName())
                .commerceRegistryNumber(agency.getCommerceRegistryNumber())
                .primaryCurrency(agency.getPrimaryCurrency())
                .createdAt(agency.getCreatedAt())
                .updatedAt(agency.getUpdatedAt())
                .build();
    }

    /**
     * Maps an {@link AgencyEntity} to an {@link Agency} domain aggregate.
     *
     * @param entity the R2DBC entity from the database
     * @return the reconstituted domain aggregate
     */
    private Agency toDomain(AgencyEntity entity) {
        return new Agency(
                OrganizationId.of(entity.getId()),
                entity.getOrganizationId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getCommerceRegistryNumber(),
                entity.getPrimaryCurrency(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
