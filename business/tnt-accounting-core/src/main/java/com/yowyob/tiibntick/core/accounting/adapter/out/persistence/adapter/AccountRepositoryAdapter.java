package com.yowyob.tiibntick.core.accounting.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.accounting.adapter.out.persistence.entity.AccountEntity;
import com.yowyob.tiibntick.core.accounting.adapter.out.persistence.repository.AccountR2dbcRepository;
import com.yowyob.tiibntick.core.accounting.application.port.out.AccountRepository;
import com.yowyob.tiibntick.core.accounting.domain.model.Account;
import com.yowyob.tiibntick.core.accounting.domain.model.AccountCategory;
import com.yowyob.tiibntick.core.accounting.domain.model.AccountType;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * R2DBC adapter implementing AccountRepository port.
 * Author: MANFOUO Braun
 */
@Component
public class AccountRepositoryAdapter implements AccountRepository {

    private final AccountR2dbcRepository repository;
    private final R2dbcEntityTemplate entityTemplate;

    public AccountRepositoryAdapter(AccountR2dbcRepository repository,
                                    R2dbcEntityTemplate entityTemplate) {
        this.repository = repository;
        this.entityTemplate = entityTemplate;
    }

    @Override
    public Mono<Account> save(Account account) {
        AccountEntity entity = toEntity(account);
        return repository.existsById(entity.id())
                .flatMap(exists -> exists
                        ? entityTemplate.update(entity)
                        : entityTemplate.insert(entity))
                .map(this::toDomain);
    }

    @Override
    public Mono<Account> findById(UUID tenantId, UUID accountId) {
        return repository.findByTenantIdAndId(tenantId, accountId).map(this::toDomain);
    }

    @Override
    public Mono<Account> findByCode(UUID tenantId, String code) {
        return repository.findByTenantIdAndCode(tenantId, code).map(this::toDomain);
    }

    @Override
    public Flux<Account> findByTenantId(UUID tenantId, boolean activeOnly) {
        return repository.findByTenantId(tenantId, activeOnly).map(this::toDomain);
    }

    @Override
    public Flux<Account> findByTenantIdAndCategory(UUID tenantId, String category) {
        return repository.findByTenantIdAndCategory(tenantId, category).map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsByCode(UUID tenantId, String code) {
        return repository.existsByTenantIdAndCode(tenantId, code);
    }

    @Override
    public Flux<Account> saveAll(Iterable<Account> accounts) {
        return Flux.fromIterable(accounts)
                .flatMap(account -> save(account));
    }

    private AccountEntity toEntity(Account account) {
        return new AccountEntity(
                account.getId(), account.getTenantId(), account.getCode(), account.getName(),
                account.getType().name(), account.getCategory().name(), account.getCurrency(),
                account.getBalance(), account.isActive(), account.getParentAccountId(),
                account.getOhadaClass(), account.getCreatedAt(), account.getUpdatedAt(),
                account.getOwnerOrgId(), account.getOwnerOrgType());
    }

    @Override
    public Flux<Account> findByOwnerOrgId(UUID tenantId, String freelancerOrgId) {
        return repository.findByTenantIdAndOwnerOrgId(tenantId, freelancerOrgId).map(this::toDomain);
    }

    private Account toDomain(AccountEntity entity) {
        return Account.rehydrateFull(
                entity.id(), entity.tenantId(), entity.code(), entity.name(),
                AccountType.valueOf(entity.type()), AccountCategory.valueOf(entity.category()),
                entity.currency(), entity.balance(), entity.active(), entity.parentAccountId(),
                entity.ohadaClass(), entity.createdAt(), entity.updatedAt(),
                entity.ownerOrgId(), entity.ownerOrgType());
    }
}
