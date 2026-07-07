package com.yowyob.tiibntick.core.administration.application.service;

import com.yowyob.tiibntick.core.administration.application.port.in.*;
import com.yowyob.tiibntick.core.administration.application.port.out.*;
import com.yowyob.tiibntick.core.administration.domain.model.*;
import com.yowyob.tiibntick.core.administration.domain.service.TntRoleTemplateRegistry;
import com.yowyob.tiibntick.core.administration.domain.service.TntRoleTemplateRegistry.TntRoleTemplate;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleInitializationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * Central application service for {@code tnt-administration-core}.
 *
 * <p>Orchestrates TiiBnTick-specific platform options, permission catalog,
 * role template provisioning, and role definition management.
 *
 * <p> — Integrations with L1 Foundation modules:
 * <ul>
 *   <li><strong>tnt-auth-core</strong>: Security context extraction via
 *       {@code ReactiveSecurityContextExtractor} for permission checks in write operations.</li>
 *   <li><strong>tnt-roles-core</strong>: {@code TntRoleInitializationService.provisionForTenant()}
 *       is called in {@link #provisionForTenant} to provision the 9 canonical {@code TntRole}
 *       definitions alongside the local {@code TntRoleDefinition} provisioning.
 *       {@code @RequirePermission} AOP guards protect write operations.</li>
 * </ul>
 *
 * <p>Kernel integration strategy:
 * <ul>
 *   <li>Role lookup: {@link KernelRolePort} — HTTP REST to RT-comops-roles-core.</li>
 *   <li>Permission resolution: {@link KernelPermissionPort} — HTTP REST to RT-comops-roles-core.</li>
 *   <li>Provisioning: Kafka event emitted → Kernel creates roles → TNT stores kernelRoleId
 *       in {@link TntRoleDefinition}.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Service
public class TntAdministrationApplicationService implements
        ListTntPermissionsUseCase,
        ManageTntPlatformOptionsUseCase,
        ProvisionTntRoleTemplatesUseCase,
        ListTntRoleDefinitionsUseCase {

    private static final Logger log = LoggerFactory.getLogger(TntAdministrationApplicationService.class);

    private final TntPlatformOptionsRepository    optionsRepository;
    private final TntAdministrationEventPublisher eventPublisher;
    private final TntRoleTemplateRegistry         roleTemplateRegistry;
    private final TntRoleDefinitionRepository     roleDefinitionRepository;
    private final KernelRolePort                  kernelRolePort;
    private final KernelPermissionPort            kernelPermissionPort;
    private final Map<String, TntPermissionEntry> permissionCatalog;

    /**
     * Optional tnt-roles-core dependency: {@code TntRoleInitializationService}.
     * Injected via reflection at runtime to avoid a hard compile dependency on tnt-roles-core's
     * implementation class in the service layer.
     * When absent (tnt-roles-core not on classpath), role provisioning proceeds with only
     * the local TntRoleDefinition logic.
     *
     * <p>Actual type: {@code com.yowyob.tiibntick.core.roles.application.service.TntRoleInitializationService}
     */
    @Autowired(required = false)
    private TntRoleInitializationService tntRoleInitializationService;

    public TntAdministrationApplicationService(
            TntPlatformOptionsRepository optionsRepository,
            TntAdministrationEventPublisher eventPublisher,
            TntRoleTemplateRegistry roleTemplateRegistry,
            TntRoleDefinitionRepository roleDefinitionRepository,
            KernelRolePort kernelRolePort,
            KernelPermissionPort kernelPermissionPort) {
        this.optionsRepository        = optionsRepository;
        this.eventPublisher           = eventPublisher;
        this.roleTemplateRegistry     = roleTemplateRegistry;
        this.roleDefinitionRepository = roleDefinitionRepository;
        this.kernelRolePort           = kernelRolePort;
        this.kernelPermissionPort     = kernelPermissionPort;
        this.permissionCatalog        = TntPermissionCatalog.buildCatalog();
        log.info("TntAdministrationApplicationService initialized — {} permissions in catalog",
                permissionCatalog.size());
    }

    // ─── ListTntPermissionsUseCase ──────────────────────────────────────────────

    @Override
    @RequirePermission(resource = "administration", action = "permissions:read")
    public Flux<TntPermissionEntry> listTntPermissions() {
        return Flux.fromIterable(permissionCatalog.values());
    }

    @Override
    @RequirePermission(resource = "administration", action = "permissions:read")
    public Flux<TntPermissionEntry> listByModule(String module) {
        return Flux.fromIterable(permissionCatalog.values())
                .filter(p -> module == null || module.equalsIgnoreCase(p.module()));
    }

    // ─── ManageTntPlatformOptionsUseCase ────────────────────────────────────────

    @Override
    @RequirePermission(resource = "administration", action = "settings:read")
    public Mono<TntPlatformOptions> getPlatformOptions(UUID tenantId) {
        return optionsRepository.findByTenantId(tenantId)
                .switchIfEmpty(Mono.defer(() -> {
                    TntPlatformOptions defaults = TntPlatformOptions.defaults(tenantId);
                    return optionsRepository.save(defaults);
                }));
    }

    @Override
    @RequirePermission(resource = "administration", action = "settings:write")
    public Mono<TntPlatformOptions> updatePlatformOptions(UUID tenantId, TntPlatformOptions options) {
        return optionsRepository.findByTenantId(tenantId)
                .switchIfEmpty(Mono.just(TntPlatformOptions.defaults(tenantId)))
                .flatMap(existing -> optionsRepository.save(options))
                .flatMap(saved -> eventPublisher.publish(tenantId,
                        "TNT_PLATFORM_OPTIONS_UPDATED", "ADMINISTRATION", saved.getId(),
                        Map.of("tenantId", tenantId.toString()))
                        .thenReturn(saved));
    }

    @Override
    @RequirePermission(resource = "admin", action = "settings")
    public Mono<TntPlatformOptions> initializeDefaultOptions(UUID tenantId) {
        return optionsRepository.findByTenantId(tenantId)
                .switchIfEmpty(Mono.defer(() -> {
                    TntPlatformOptions defaults = TntPlatformOptions.defaults(tenantId);
                    return optionsRepository.save(defaults)
                            .flatMap(saved -> eventPublisher.publish(tenantId,
                                    "TNT_PLATFORM_OPTIONS_INITIALIZED", "ADMINISTRATION", saved.getId(),
                                    Map.of("tenantId", tenantId.toString()))
                                    .thenReturn(saved));
                }));
    }

    // ─── ProvisionTntRoleTemplatesUseCase ───────────────────────────────────────

    @Override
    @RequirePermission(resource = "administration", action = "roles:read")
    public Flux<TntRoleTemplate> listTemplates() {
        return Flux.fromIterable(roleTemplateRegistry.getTemplates());
    }

    /**
     * Provisions TiiBnTick role templates for a new tenant.
     *
     * <p> — This method now performs a two-level provisioning:
     * <ol>
     *   <li><strong>Local TntRoleDefinition provisioning</strong> (existing behavior):
     *       For each template in {@code TntRoleTemplateRegistry}, creates a local
     *       {@code TntRoleDefinition} and optionally links to an existing Kernel role.</li>
     *   <li><strong>tnt-roles-core canonical provisioning</strong> (new in ):
     *       Calls {@code TntRoleInitializationService.provisionForTenant(tenantId)} to
     *       provision the 9 canonical {@code TntRole} definitions in the Kernel DB
     *       (RT-comops-roles-core) for this tenant. This is idempotent — existing definitions
     *       are skipped.</li>
     * </ol>
     *
     * @param tenantId       the tenant UUID to provision for
     * @param organizationId the organization UUID creating the tenant
     * @param actorUserId    the admin user UUID initiating provisioning
     * @return empty Mono on completion
     */
    @Override
    @RequirePermission(resource = "administration", action = "roles:write")
    public Mono<Void> provisionForTenant(UUID tenantId, UUID organizationId, UUID actorUserId) {
        // Step 1: Local TntRoleDefinition provisioning (existing logic)
        Flux<TntRoleDefinition> localProvisionFlux = Flux.fromIterable(roleTemplateRegistry.getTemplates())
                .flatMap(template -> roleDefinitionRepository
                        .existsByTenantIdAndTemplateCode(tenantId, template.code())
                        .flatMap(alreadyProvisioned -> {
                            if (alreadyProvisioned) {
                                log.debug("Role template {} already provisioned for tenant {}",
                                        template.code(), tenantId);
                                return Mono.<TntRoleDefinition>empty();
                            }
                            TntRoleDefinition definition = TntRoleDefinition.provision(
                                    tenantId, template.code(), template.name(),
                                    template.scopeType(), template.permissions(),
                                    template.protectedTemplate());
                            return kernelRolePort.findByCodeAndTenant(template.code(), tenantId)
                                    .map(kernelRole -> definition.withKernelRoleId(kernelRole.roleId()))
                                    .switchIfEmpty(Mono.just(definition))
                                    .flatMap(roleDefinitionRepository::save);
                        }));

        return localProvisionFlux
                .collectList()
                .flatMap(definitions -> {
                    log.info("Local TntRoleDefinition provisioning complete for tenant {} — {} templates processed",
                            tenantId, definitions.size());

                    // Step 2: tnt-roles-core canonical provisioning (new in )
                    // Delegates to TntRoleInitializationService.provisionForTenant() when available
                    return invokeRolesCoreProvisioning(tenantId)
                            .then(eventPublisher.publish(
                                    tenantId,
                                    "TNT_ROLE_TEMPLATES_PROVISION_REQUESTED",
                                    "ADMINISTRATION",
                                    tenantId,
                                    Map.of("tenantId",       tenantId.toString(),
                                           "organizationId", organizationId.toString(),
                                           "actorUserId",    actorUserId.toString(),
                                           "templateCount",  roleTemplateRegistry.getTemplates().size(),
                                           "newlyCreated",   definitions.size())));
                })
                .then();
    }

    // ─── ListTntRoleDefinitionsUseCase ───────────────────────────────────────────

    @Override
    @RequirePermission(resource = "administration", action = "roles:read")
    public Flux<TntRoleDefinition> listByTenant(UUID tenantId) {
        return roleDefinitionRepository.findAllByTenantId(tenantId);
    }

    @Override
    @RequirePermission(resource = "administration", action = "roles:read")
    public Mono<TntRoleDefinition> getById(UUID definitionId) {
        return roleDefinitionRepository.findById(definitionId);
    }

    // ─── Convenience queries ─────────────────────────────────────────────────────

    /** Returns a single TNT permission entry by code, or an error if unknown. */
    public Mono<TntPermissionEntry> getPermission(String code) {
        TntPermissionEntry entry = permissionCatalog.get(code != null ? code.toLowerCase() : "");
        return entry != null ? Mono.just(entry)
                : Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown TiiBnTick permission: " + code));
    }

    /** Returns true if the given permission code is a system-protected TNT permission. */
    public boolean isProtectedPermission(String code) {
        TntPermissionEntry entry = permissionCatalog.get(code != null ? code.toLowerCase() : "");
        return entry != null && entry.system();
    }

    /**
     * Resolves the kernelPermissionId for a TNT permission code by querying the Kernel.
     * Returns empty if the permission has no Kernel counterpart (TNT-exclusive permissions).
     */
    public Mono<TntPermissionEntry> resolveKernelPermissionId(String code) {
        TntPermissionEntry entry = permissionCatalog.get(code != null ? code.toLowerCase() : "");
        if (entry == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown TNT permission: " + code));
        }
        if (entry.system()) {
            return Mono.just(entry);
        }
        return kernelPermissionPort.findByCode(code)
                .map(kernelPerm -> entry.withKernelPermissionId(kernelPerm.permissionId()))
                .switchIfEmpty(Mono.just(entry))
                .onErrorReturn(entry);
    }

    /**
     * Returns the total number of permissions in the catalog.
     * Used by health indicators.
     */
    public int getPermissionCatalogSize() {
        return permissionCatalog.size();
    }

    // ─── tnt-roles-core delegation ───────────────────────────────────────────────

    /**
     * Delegates canonical role provisioning to {@code TntRoleInitializationService}
     * from {@code tnt-roles-core} via reflection, to avoid a hard compile dependency.
     *
     * <p>Calls {@code TntRoleInitializationService.provisionForTenant(UUID tenantId)}.
     * If tnt-roles-core is absent (test environments), logs a warning and proceeds.
     *
     * @param tenantId the tenant UUID to provision canonical roles for
     * @return empty Mono on completion
     */
    private Mono<Void> invokeRolesCoreProvisioning(UUID tenantId) {
        if (tntRoleInitializationService == null) {
            log.debug("TntRoleInitializationService absent — skipping canonical role provisioning " +
                      "via tnt-roles-core for tenant {}", tenantId);
            return Mono.empty();
        }
        return Mono.fromCallable(() -> {
            try {
                Method provisionMethod = tntRoleInitializationService.getClass()
                        .getMethod("provisionForTenant", UUID.class);
                Object result = provisionMethod.invoke(tntRoleInitializationService, tenantId);
                // Block the reactive pipeline if the result is a Mono
                if (result instanceof reactor.core.publisher.Mono<?> mono) {
                    mono.block();
                }
                log.info("Canonical TntRole provisioning via tnt-roles-core complete for tenant {}", tenantId);
                return (Void) null;
            } catch (NoSuchMethodException e) {
                log.warn("TntRoleInitializationService.provisionForTenant(UUID) not found — API mismatch. " +
                         "Canonical roles will be provisioned asynchronously on ApplicationReadyEvent.");
                return (Void) null;
            } catch (Exception e) {
                log.warn("tnt-roles-core canonical provisioning failed for tenant {}: {}. " +
                         "Local TntRoleDefinitions were still created successfully.",
                        tenantId, e.getMessage());
                return (Void) null;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
