package com.yowyob.tiibntick.core.accounting.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.accounting.adapter.out.persistence.entity.AccountingPeriodEntity;
import com.yowyob.tiibntick.core.accounting.adapter.out.persistence.repository.AccountingPeriodR2dbcRepository;
import com.yowyob.tiibntick.core.accounting.application.port.out.AccountingPeriodRepository;
import com.yowyob.tiibntick.core.accounting.domain.model.AccountingPeriod;
import com.yowyob.tiibntick.core.accounting.domain.model.PeriodStatus;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * R2DBC adapter for AccountingPeriod persistence.
 * Author: MANFOUO Braun
 */
@Component
public class AccountingPeriodRepositoryAdapter implements AccountingPeriodRepository {

    private final AccountingPeriodR2dbcRepository repository;
    private final R2dbcEntityTemplate entityTemplate;

    public AccountingPeriodRepositoryAdapter(AccountingPeriodR2dbcRepository repository,
                                             R2dbcEntityTemplate entityTemplate) {
        this.repository = repository;
        this.entityTemplate = entityTemplate;
    }

    @Override
    public Mono<AccountingPeriod> save(AccountingPeriod period) {
        var entity = toEntity(period);
        return repository.existsById(entity.id())
                .flatMap(exists -> exists
                        ? entityTemplate.update(entity)
                        : entityTemplate.insert(entity))
                .map(this::toDomain);
    }

    @Override
    public Mono<AccountingPeriod> findByTenantIdAndYearAndMonth(UUID tenantId, int year, int month) {
        return repository.findByTenantIdAndYearAndMonth(tenantId, year, month).map(this::toDomain);
    }

    @Override
    public Mono<AccountingPeriod> findOrCreateOpen(UUID tenantId, int year, int month) {
        return findByTenantIdAndYearAndMonth(tenantId, year, month)
                .switchIfEmpty(Mono.defer(() -> {
                    AccountingPeriod newPeriod = AccountingPeriod.open(tenantId, year, month);
                    return save(newPeriod);
                }));
    }

    private AccountingPeriodEntity toEntity(AccountingPeriod period) {
        return new AccountingPeriodEntity(period.getId(), period.getTenantId(), period.getYear(),
                period.getMonth(), period.getStatus().name(), period.getOpenedAt(),
                period.getClosedAt(), period.getCreatedAt(), period.getUpdatedAt());
    }

    private AccountingPeriod toDomain(AccountingPeriodEntity entity) {
        return AccountingPeriod.rehydrate(entity.id(), entity.tenantId(), entity.year(),
                entity.month(), PeriodStatus.valueOf(entity.status()), entity.openedAt(),
                entity.closedAt(), entity.createdAt(), entity.updatedAt());
    }
}
