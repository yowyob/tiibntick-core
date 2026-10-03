package com.yowyob.tiibntick.core.roles.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.domain.model.Role;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncAggregateType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOperation;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
import com.yowyob.tiibntick.core.roles.domain.model.TntRoleDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Startup service that seeds and reconciles TiiBnTick's canonical role definitions into the
 * local RBAC store for the system tenant.
 *
 * <p><strong>Authoritative source of truth:</strong> {@link com.yowyob.tiibntick.core.roles.domain.model.TntRole}
 * is the sole, definitive source for system role definitions and their permission sets. No
 * Liquibase migration must ever {@code INSERT INTO tnt_roles} — doing so bypasses this
 * reconciliation and produces a permanently amputated role whose permissions are never updated
 * because {@code existsByCode} returns {@code true} on the first boot after the migration runs.
 *
 * <p>Per-role reconciliation logic (three-way):
 * <ol>
 *   <li>Role absent → {@link #provisionAndEnqueue} (current behaviour, unchanged).</li>
 *   <li>Role present, {@code editable=true} (tenant-created) → no-op; tenant-owned roles must
 *       never be overwritten.</li>
 *   <li>Role present, {@code editable=false} (system role), permissions equal canonical →
 *       no-op ({@code DEBUG} log).</li>
 *   <li>Role present, {@code editable=false} (system role), permissions differ from canonical →
 *       update to canonical set, {@code INFO} log listing added/removed permissions, enqueue
 *       {@link RoleSyncOperation#UPDATE_ROLE} outbox entry as audit record.</li>
 * </ol>
 *
 * <p>Triggered after Spring Boot application context is fully ready
 * ({@link ApplicationReadyEvent}), running asynchronously on the {@code boundedElastic}
 * scheduler to avoid blocking the main context startup.
 *
 * <p>For per-tenant provisioning (when a new organization is onboarded),
 * {@code tnt-administration-core} calls {@link #provisionForTenant(UUID)} explicitly — same
 * reconcile-or-provision logic, scoped to the given tenant.
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
        log.info("TntRoleInitializationService: reconciling {} TiiBnTick role definitions for system tenant {}",
                registry.size(), systemTenantId);

        provisionForTenant(systemTenantId)
                .then(reconcileTenantCopies())
                .doOnSuccess(v -> log.info("TntRoleInitializationService: reconciliation complete."))
                .doOnError(err -> log.error("TntRoleInitializationService: reconciliation error: {}", err.getMessage(), err))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    /**
     * Reconciles TiiBnTick's canonical role definitions for a specific tenant — idempotent,
     * safe to call repeatedly. Called at startup for the system tenant, and by
     * {@code tnt-administration-core} when a new tenant is onboarded.
     *
     * @param tenantId the tenant to reconcile roles for
     * @return a Mono that completes when reconciliation is done
     */
    public Mono<Void> provisionForTenant(UUID tenantId) {
        log.info("Reconciling TiiBnTick roles for tenant {}", tenantId);
        return Flux.fromIterable(registry.getAllDefinitions())
                .concatMap(definition -> reconcileOrProvision(tenantId, definition))
                .then();
    }

    /**
     * Lot C-21 — reconciles (never provisions) the canonical roles that already exist in
     * tenants other than the system tenant: the copies {@link #provisionForTenant} created
     * at agency onboarding. Without this pass, a permission added to {@link TntRole} after a
     * tenant's onboarding never reaches that tenant's holders, since nothing re-runs
     * {@code provisionForTenant} for it. Tenants without a copy are left alone — creating
     * rows (and Kernel PROVISION_ROLE outbox entries) for them is onboarding's job.
     */
    public Mono<Void> reconcileTenantCopies() {
        return Flux.fromIterable(registry.getAllDefinitions())
                .concatMap(definition -> roleRepository.findAllByCode(definition.code())
                        .filter(existing -> !systemTenantId.equals(existing.tenantId()))
                        .concatMap(existing -> reconcileExisting(existing.tenantId(), existing, definition)))
                .then();
    }

    private Mono<Void> reconcileOrProvision(UUID tenantId, TntRoleDefinition definition) {
        // flatMap always emits Boolean.TRUE to distinguish "role was found and handled"
        // from "role absent" — Mono<Void> is empty by definition, so switchIfEmpty would
        // incorrectly trigger on no-op paths if we used Mono<Void> directly.
        return roleRepository.findByCode(tenantId, definition.code())
                .flatMap(existing -> reconcileExisting(tenantId, existing, definition))
                .switchIfEmpty(Mono.defer(() -> provisionAndEnqueue(tenantId, definition).thenReturn(Boolean.TRUE)))
                .then();
    }

    private Mono<Boolean> reconcileExisting(UUID tenantId, Role existing, TntRoleDefinition definition) {
        if (!TntRole.isKnownRole(existing.code())) {
            // Custom role created by tenant — never overwrite, regardless of editable flag.
            log.debug("Role {} for tenant {} is not a canonical TntRole (custom/unknown), skipping.",
                    existing.code(), tenantId);
            return Mono.just(Boolean.TRUE);
        }
        if (existing.editable()) {
            // Canonical code but editable=true in DB: could be a pre-C18 row
            // provisioned by toNewEntity() before it respected the editable field.
            // We still reconcile it — canonical roles must always match TntRole.
            log.debug("Role {} for tenant {} has editable=true in DB (pre-C18 row), reconciling anyway.",
                    existing.code(), tenantId);
        }
        if (existing.permissions().equals(definition.defaultPermissions())) {
            log.debug("Role {} already has canonical permissions for tenant {}, no-op.",
                    definition.code(), tenantId);
            return Mono.just(Boolean.TRUE);
        }
        Set<String> added = new HashSet<>(definition.defaultPermissions());
        added.removeAll(existing.permissions());
        Set<String> removed = new HashSet<>(existing.permissions());
        removed.removeAll(definition.defaultPermissions());
        log.info("Reconciling permissions for system role {} (tenant {}): added={}, removed={}",
                definition.code(), tenantId, added, removed);
        return reconcileAndEnqueue(tenantId, existing, definition).thenReturn(Boolean.TRUE);
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

    private Mono<Void> reconcileAndEnqueue(UUID tenantId, Role existing, TntRoleDefinition definition) {
        Role updated = new Role(existing.id(), tenantId, existing.code(), existing.name(),
                existing.scopeType(), definition.defaultPermissions(), false);
        String payload = RoleSyncPayloads.toJson(objectMapper,
                new RoleSyncPayloads.ProvisionRolePayload(tenantId, updated.code(), updated.name(),
                        updated.scopeType().name(), updated.permissions()));
        RoleSyncOutboxEntry outboxEntry = RoleSyncOutboxEntry.pending(
                RoleSyncOperation.UPDATE_ROLE, RoleSyncAggregateType.ROLE, updated.id(), tenantId, payload);

        return roleRepository.save(updated)
                .flatMap(saved -> outboxRepository.save(outboxEntry))
                .as(transactionalOperator::transactional)
                .doOnNext(entry -> log.debug("Reconciled system role {} for tenant {}, enqueued UPDATE_ROLE outbox entry {}",
                        updated.code(), tenantId, entry.id()))
                .then();
    }
}
