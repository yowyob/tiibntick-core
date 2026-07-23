package com.yowyob.tiibntick.core.roles.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.domain.model.Role;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncAggregateType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOperation;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.TntRoleDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * Startup service that seeds TiiBnTick's canonical role definitions into the local RBAC
 * store for the system tenant.
 *
 * <p>Local RBAC persistence (Chantier D · Audit n°6 · S5): for each of the 9 canonical
 * {@link TntRoleDefinition}s, upserts a local {@link Role} row (idempotent — {@code
 * RoleRepository#existsByCode} then {@code save} only if absent, safe to re-run every boot)
 * and, only when the row didn't already exist, enqueues a {@code
 * RoleSyncOutboxEntry(PROVISION_ROLE)} for asynchronous replay to the Kernel by a separately
 * built sync worker. This service no longer calls the Kernel directly.
 *
 * <p>Triggered after Spring Boot application context is fully ready
 * ({@link ApplicationReadyEvent}), running asynchronously on the {@code boundedElastic}
 * scheduler to avoid blocking the main context startup.
 *
 * <p>For per-tenant provisioning (when a new organization is onboarded),
 * {@code tnt-administration-core} calls {@link #provisionForTenant(UUID)} explicitly — same
 * upsert-if-absent + conditional-enqueue logic, scoped to the given tenant.
 *
 * @author MANFOUO Braun
 */
public class TntRoleInitializationService {

    private static final Logger log = LoggerFactory.getLogger(TntRoleInitializationService.class);

    private final TntRoleDefinitionRegistry registry;
    private final RoleRepository roleRepository;
    private final RoleSyncOutboxRepository outboxRepository;
    private final TransactionalOperator transactionalOperator;
    private final ObjectMapper objectMapper;
    private final UUID systemTenantId;

    public TntRoleInitializationService(
            TntRoleDefinitionRegistry registry,
            RoleRepository roleRepository,
            RoleSyncOutboxRepository outboxRepository,
            TransactionalOperator transactionalOperator,
            ObjectMapper objectMapper,
            UUID systemTenantId) {
        this.registry = registry;
        this.roleRepository = roleRepository;
        this.outboxRepository = outboxRepository;
        this.transactionalOperator = transactionalOperator;
        this.objectMapper = objectMapper;
        this.systemTenantId = systemTenantId;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void provisionSystemRoles() {
        log.info("TntRoleInitializationService: provisioning {} TiiBnTick role definitions for system tenant {}",
                registry.size(), systemTenantId);

        provisionForTenant(systemTenantId)
                .doOnSuccess(v -> log.info("TntRoleInitializationService: provisioning complete."))
                .doOnError(err -> log.error("TntRoleInitializationService: provisioning error: {}", err.getMessage(), err))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    /**
     * Upserts TiiBnTick's canonical role definitions for a specific tenant — idempotent, safe
     * to call repeatedly. Called at startup for the system tenant, and by
     * {@code tnt-administration-core} when a new tenant is onboarded.
     *
     * @param tenantId the tenant to provision roles for
     * @return a Mono that completes when provisioning is done
     */
    public Mono<Void> provisionForTenant(UUID tenantId) {
        log.info("Provisioning TiiBnTick roles for tenant {}", tenantId);
        return Flux.fromIterable(registry.getAllDefinitions())
                .concatMap(definition -> upsertIfAbsent(tenantId, definition))
                .then();
    }

    private Mono<Void> upsertIfAbsent(UUID tenantId, TntRoleDefinition definition) {
        return roleRepository.existsByCode(tenantId, definition.code())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.debug("Role {} already provisioned for tenant {}, skipping.", definition.code(), tenantId);
                        return Mono.empty();
                    }
                    return provisionAndEnqueue(tenantId, definition);
                });
    }

    private Mono<Void> provisionAndEnqueue(UUID tenantId, TntRoleDefinition definition) {
        Role role = Role.create(tenantId, definition.code(), definition.name(), definition.scopeType(), definition.defaultPermissions());
        String payload = RoleSyncPayloads.toJson(objectMapper,
                new RoleSyncPayloads.ProvisionRolePayload(tenantId, role.code(), role.name(), role.scopeType().name(), role.permissions()));
        RoleSyncOutboxEntry outboxEntry = RoleSyncOutboxEntry.pending(
                RoleSyncOperation.PROVISION_ROLE, RoleSyncAggregateType.ROLE, role.id(), tenantId, payload);

        return roleRepository.save(role)
                .flatMap(saved -> outboxRepository.save(outboxEntry))
                .as(transactionalOperator::transactional)
                .doOnNext(entry -> log.debug("Provisioned TiiBnTick role {} for tenant {}, enqueued outbox entry {}",
                        role.code(), tenantId, entry.id()))
                .then();
    }
}
