package com.yowyob.tiibntick.core.accounting.application.port.out;

import com.yowyob.tiibntick.core.accounting.domain.model.Account;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for Account persistence.
 * Author: MANFOUO Braun
 */
public interface AccountRepository {

    Mono<Account> save(Account account);

    Mono<Account> findById(UUID tenantId, UUID accountId);

    Mono<Account> findByCode(UUID tenantId, String code);

    Flux<Account> findByTenantId(UUID tenantId, boolean activeOnly);

    Flux<Account> findByTenantIdAndCategory(UUID tenantId, String category);

    Mono<Boolean> existsByCode(UUID tenantId, String code);

    Flux<Account> saveAll(Iterable<Account> accounts);
    /**
     * Finds all accounts owned by a specific FreelancerOrg ().
     *
     * @param tenantId        tenant scope
     * @param freelancerOrgId the FreelancerOrg UUID
     * @return Flux of org-specific OHADA accounts (411-FRL, 421-FRL, 706-FRL)
     */
    Flux<Account> findByOwnerOrgId(UUID tenantId, String freelancerOrgId);

}