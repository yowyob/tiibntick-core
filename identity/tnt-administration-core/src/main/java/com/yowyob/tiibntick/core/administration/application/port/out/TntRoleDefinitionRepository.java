package com.yowyob.tiibntick.core.administration.application.port.out;

import com.yowyob.tiibntick.core.administration.domain.model.TntRoleDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for persisting and querying {@link TntRoleDefinition} aggregates.
 *
 * <p>TntRoleDefinitions track the provisioning of TNT role templates for specific tenants
 * and their linkage to Kernel roles via {@code kernelRoleId}.
 *
 * @author MANFOUO Braun
 */
public interface TntRoleDefinitionRepository {

    /**
     * Saves a new or updated TntRoleDefinition.
     *
     * @param definition the role definition to persist
     * @return the saved role definition
     */
    Mono<TntRoleDefinition> save(TntRoleDefinition definition);

    /**
     * Finds a TntRoleDefinition by its unique ID.
     *
     * @param id the UUID of the TntRoleDefinition
     * @return the role definition, or empty if not found
     */
    Mono<TntRoleDefinition> findById(UUID id);

    /**
     * Lists all TntRoleDefinitions provisioned for a tenant.
     *
     * @param tenantId the tenant UUID
     * @return flux of role definitions for the tenant
     */
    Flux<TntRoleDefinition> findAllByTenantId(UUID tenantId);

    /**
     * Finds a TntRoleDefinition by tenant and template code.
     *
     * @param tenantId     the tenant UUID
     * @param templateCode the TNT role template code
     * @return the role definition, or empty if not provisioned
     */
    Mono<TntRoleDefinition> findByTenantIdAndTemplateCode(UUID tenantId, String templateCode);

    /**
     * Checks whether a role definition has already been provisioned for a tenant/template pair.
     *
     * @param tenantId     the tenant UUID
     * @param templateCode the TNT role template code
     * @return true if already provisioned, false otherwise
     */
    Mono<Boolean> existsByTenantIdAndTemplateCode(UUID tenantId, String templateCode);

    /**
     * Lists all TntRoleDefinitions that are pending Kernel synchronization
     * (kernelSynced = false).
     *
     * @return flux of unsynced role definitions
     */
    Flux<TntRoleDefinition> findAllPendingKernelSync();
}
