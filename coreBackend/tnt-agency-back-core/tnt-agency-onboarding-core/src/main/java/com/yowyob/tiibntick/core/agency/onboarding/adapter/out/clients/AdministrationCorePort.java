package com.yowyob.tiibntick.core.agency.onboarding.adapter.out.clients;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * TiiBnTick Core administration — tenant provisioning after agency approval.
 */
public interface AdministrationCorePort {

    Mono<Void> provisionRoleTemplates(UUID tenantId, UUID organizationId, UUID actorUserId);

    Mono<Void> initializePlatformOptions(UUID tenantId);
}
