package com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence;

import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.entity.ClientAuditLogEntity;
import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.mapper.PlatformClientPersistenceMapper;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IClientAuditLogRepository;
import com.yowyob.tiibntick.core.platformgateway.domain.model.AuditOutcome;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientAuditLog;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

/**
 * Implements {@link IClientAuditLogRepository} — filtered/paginated listing via
 * {@link R2dbcEntityTemplate}'s dynamic {@link Criteria} (see
 * {@code PlatformClientRepositoryAdapter} for why not a static {@code @Query}).
 *
 * @author MANFOUO Braun
 */
public class ClientAuditLogRepositoryAdapter implements IClientAuditLogRepository {

    private final ClientAuditLogR2dbcRepository repository;
    private final R2dbcEntityTemplate template;

    public ClientAuditLogRepositoryAdapter(ClientAuditLogR2dbcRepository repository, R2dbcEntityTemplate template) {
        this.repository = repository;
        this.template = template;
    }

    @Override
    public Mono<ClientAuditLog> save(ClientAuditLog log) {
        return repository.save(PlatformClientPersistenceMapper.toEntity(log))
                .map(PlatformClientPersistenceMapper::toDomain);
    }

    @Override
    public Flux<ClientAuditLog> findPage(UUID platformClientId, AuditOutcome outcome, Instant from, Instant to, int page, int size) {
        Query q = query(criteria(platformClientId, outcome, from, to))
                .sort(Sort.by(Sort.Direction.DESC, "occurred_at"))
                .offset((long) page * size)
                .limit(size);
        return template.select(q, ClientAuditLogEntity.class).map(PlatformClientPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Long> count(UUID platformClientId, AuditOutcome outcome, Instant from, Instant to) {
        return template.count(query(criteria(platformClientId, outcome, from, to)), ClientAuditLogEntity.class);
    }

    private Criteria criteria(UUID platformClientId, AuditOutcome outcome, Instant from, Instant to) {
        Criteria criteria = Criteria.empty();
        if (platformClientId != null) {
            criteria = criteria.and(where("platform_client_id").is(platformClientId.toString()));
        }
        if (outcome != null) {
            criteria = criteria.and(where("outcome").is(outcome.name()));
        }
        if (from != null) {
            criteria = criteria.and(where("occurred_at").greaterThanOrEquals(from));
        }
        if (to != null) {
            criteria = criteria.and(where("occurred_at").lessThanOrEquals(to));
        }
        return criteria;
    }
}
