package com.yowyob.tiibntick.core.administration.application.port.in;

import com.yowyob.tiibntick.core.administration.domain.model.TntRoleDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;


/**
 * Lists and queries TntRoleDefinitions — the persisted records of provisioned
 * roles per tenant.
 *
 * @author MANFOUO Braun
 */
public interface ListTntRoleDefinitionsUseCase {
    /** Lists all provisioned TntRoleDefinitions for a tenant. */
    Flux<TntRoleDefinition> listByTenant(UUID tenantId);

    /** Finds a specific TntRoleDefinition by its ID. */
    Mono<TntRoleDefinition> getById(UUID definitionId);
}
