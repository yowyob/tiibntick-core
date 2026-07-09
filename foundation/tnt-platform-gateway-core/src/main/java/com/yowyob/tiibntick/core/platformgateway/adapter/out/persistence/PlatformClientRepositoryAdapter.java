package com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence;

import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.entity.PlatformClientEntity;
import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.mapper.PlatformClientPersistenceMapper;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IPlatformClientRepository;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientStatus;
import com.yowyob.tiibntick.core.platformgateway.domain.model.Environment;
import com.yowyob.tiibntick.core.platformgateway.domain.model.PlatformClient;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

/**
 * Implements {@link IPlatformClientRepository} — CRUD/simple lookups via
 * {@link PlatformClientR2dbcRepository}, filtered/paginated listing via
 * {@link R2dbcEntityTemplate}'s dynamic {@link Criteria} (only adds a clause for
 * filters actually present, sidestepping the R2DBC Postgres null-bind-parameter
 * pitfall a static {@code @Query} with optional filters would hit).
 *
 * @author MANFOUO Braun
 */
public class PlatformClientRepositoryAdapter implements IPlatformClientRepository {

    private final PlatformClientR2dbcRepository repository;
    private final R2dbcEntityTemplate template;

    public PlatformClientRepositoryAdapter(PlatformClientR2dbcRepository repository, R2dbcEntityTemplate template) {
        this.repository = repository;
        this.template = template;
    }

    @Override
    public Mono<PlatformClient> save(PlatformClient client) {
        return repository.save(PlatformClientPersistenceMapper.toEntity(client))
                .map(PlatformClientPersistenceMapper::toDomain);
    }

    @Override
    public Mono<PlatformClient> findById(UUID id) {
        return repository.findById(id.toString())
                .map(PlatformClientPersistenceMapper::toDomain);
    }

    @Override
    public Mono<PlatformClient> findByClientId(String clientId) {
        return repository.findByClientId(clientId)
                .map(PlatformClientPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Boolean> existsByClientId(String clientId) {
        return repository.existsByClientId(clientId);
    }

    @Override
    public Flux<PlatformClient> findPage(String platformCode, Environment environment, ClientStatus status, int page, int size) {
        Query q = query(criteria(platformCode, environment, status))
                .sort(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "created_at"))
                .offset((long) page * size)
                .limit(size);
        return template.select(q, PlatformClientEntity.class).map(PlatformClientPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Long> count(String platformCode, Environment environment, ClientStatus status) {
        return template.count(query(criteria(platformCode, environment, status)), PlatformClientEntity.class);
    }

    private Criteria criteria(String platformCode, Environment environment, ClientStatus status) {
        Criteria criteria = Criteria.empty();
        if (platformCode != null && !platformCode.isBlank()) {
            criteria = criteria.and(where("platform_code").is(platformCode));
        }
        if (environment != null) {
            criteria = criteria.and(where("environment").is(environment.name()));
        }
        if (status != null) {
            criteria = criteria.and(where("status").is(status.name()));
        }
        return criteria;
    }
}
