package com.yowyob.tiibntick.core.administration.application.port.in;

import com.yowyob.tiibntick.core.administration.domain.model.TntRoleDefinition;
import com.yowyob.tiibntick.core.administration.domain.service.TntRoleTemplateRegistry.TntRoleTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Provisions TiiBnTick-specific role templates for a tenant and queries
 * provisioned definitions.
 *
 * <p>
 * Provisioning emits a Kafka event that triggers role creation in the Kernel
 * (RT-comops-roles-core). Provisioned definitions are tracked locally as
 * {@link TntRoleDefinition} with the Kernel role UUID stored in
 * {@code kernelRoleId}.
 *
 * @author MANFOUO Braun
 */
public interface ProvisionTntRoleTemplatesUseCase {
    /** Returns the static list of TNT role templates (no Kernel lookup). */
    Flux<TntRoleTemplate> listTemplates();

    /**
     * Provisions all TNT role templates for a tenant.
     * Creates TntRoleDefinitions for each template and emits a Kafka event.
     */
    Mono<Void> provisionForTenant(UUID tenantId, UUID organizationId, UUID actorUserId);
}