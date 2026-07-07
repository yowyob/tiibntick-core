package com.yowyob.tiibntick.core.roles.application.service;

import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleProvisioningPort;
import com.yowyob.tiibntick.core.roles.domain.model.TntRoleDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;

/**
 * Startup service that provisions TiiBnTick system-level role definitions
 * into the Kernel DB for the system tenant.
 *
 * <p>Triggered after Spring Boot application context is fully ready
 * ({@link ApplicationReadyEvent}). Uses the provisioning port which calls the
 * Kernel's {@code CreateRoleUseCase} — idempotent, safe to re-run on every start.
 *
 * <p>For per-tenant provisioning (when a new organization is onboarded),
 * {@code tnt-administration-core} calls
 * {@link ITntRoleProvisioningPort#provisionAll(UUID, List)} explicitly.
 *
 * <p>Provisioning runs asynchronously on the {@code boundedElastic} scheduler
 * to avoid blocking the main context startup.
 *
 * @author MANFOUO Braun
 */
public class TntRoleInitializationService {

    private static final Logger log = LoggerFactory.getLogger(TntRoleInitializationService.class);

    private final TntRoleDefinitionRegistry registry;
    private final ITntRoleProvisioningPort provisioningPort;
    private final UUID systemTenantId;

    public TntRoleInitializationService(
            TntRoleDefinitionRegistry registry,
            ITntRoleProvisioningPort provisioningPort,
            UUID systemTenantId) {
        this.registry = registry;
        this.provisioningPort = provisioningPort;
        this.systemTenantId = systemTenantId;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void provisionSystemRoles() {
        log.info("TntRoleInitializationService: provisioning {} TiiBnTick role definitions for system tenant {}",
                registry.size(), systemTenantId);

        List<TntRoleDefinition> allDefinitions = registry.getAllDefinitions();

        provisioningPort.provisionAll(systemTenantId, allDefinitions)
                .doOnNext(provisioned -> log.debug("Provisioned TiiBnTick role: {}", provisioned))
                .doOnComplete(() -> log.info("TntRoleInitializationService: provisioning complete."))
                .doOnError(err -> log.error("TntRoleInitializationService: provisioning error: {}", err.getMessage(), err))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    /**
     * Provisions TiiBnTick roles for a specific tenant.
     * Called by tnt-administration-core when a new tenant is onboarded.
     *
     * @param tenantId the tenant to provision roles for
     * @return a Mono that completes when provisioning is done
     */
    public Mono<Void> provisionForTenant(UUID tenantId) {
        log.info("Provisioning TiiBnTick roles for tenant {}", tenantId);
        return provisioningPort.provisionAll(tenantId, registry.getAllDefinitions())
                .doOnNext(code -> log.debug("Provisioned role {} for tenant {}", code, tenantId))
                .then();
    }
}
