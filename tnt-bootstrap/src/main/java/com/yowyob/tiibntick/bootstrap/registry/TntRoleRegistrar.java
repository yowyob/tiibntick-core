package com.yowyob.tiibntick.bootstrap.registry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Registers TiiBnTick-specific RBAC roles in the Yowyob Kernel at startup.
 *
 * <p><strong> — Delegation to tnt-roles-core:</strong><br>
 * When {@code tnt-roles-core} is on the classpath (production deployments),
 * this registrar delegates provisioning to {@code TntRoleInitializationService}
 * which handles:
 * <ul>
 *   <li>Idempotent role definition seeding in the Kernel DB (PostgreSQL)</li>
 *   <li>Provisioning of the 9 canonical {@code TntRole} definitions</li>
 *   <li>Redis permission cache invalidation after provisioning</li>
 * </ul>
 *
 * <p>When {@code tnt-roles-core} is absent (test slices, isolated unit tests),
 * falls back to logging the local {@link TntRoleDefinition#ALL_ROLES} for visibility.
 *
 * <p>This bean is invoked at step 5 of the startup sequence by
 * {@link com.yowyob.tiibntick.bootstrap.startup.TntStartupRunner}.
 * {@code TntRoleInitializationService} also self-triggers on {@code ApplicationReadyEvent}
 * for full async provisioning after context startup.
 *
 * @author MANFOUO Braun
 * @see TntRoleDefinition
 */
@Slf4j
@Component
@SuppressWarnings("deprecation")
public class TntRoleRegistrar {

    @Value("${tnt.roles.provision-on-startup:true}")
    private boolean provisionOnStartup;

    @Value("${tnt.roles.system-tenant-id:00000000-0000-0000-0000-000000000001}")
    private String systemTenantId;

    /**
     * Injected lazily from tnt-roles-core via class name to avoid a hard compile-time
     * dependency. The actual type is:
     * {@code com.yowyob.tiibntick.core.roles.application.service.TntRoleInitializationService}.
     *
     * <p>When absent (tnt-roles-core not on classpath), {@code registerAll()} falls back
     * to local definition logging.
     */
    @Autowired(required = false)
    @Qualifier("tntRoleInitializationService")
    private Object tntRoleInitializationService;

    /**
     * Registers all TiiBnTick canonical roles.
     * Called by {@link com.yowyob.tiibntick.bootstrap.startup.TntStartupRunner}
     * at step 5 of the startup sequence.
     *
     * <p>Behavior:
     * <ol>
     *   <li>If {@code tnt-roles-core} is present and {@code provision-on-startup=true}:
     *       calls {@code TntRoleInitializationService.provisionSystemRoles()} synchronously
     *       (blocking on a bounded-elastic scheduler) to give immediate feedback in the
     *       startup log.</li>
     *   <li>If {@code tnt-roles-core} is absent: logs the 9 local role definitions for
     *       visibility and exits. Suitable for test environments.</li>
     * </ol>
     */
    public void registerAll() {
        log.info("Starting TiiBnTick role registration — {} canonical roles defined",
                TntRoleDefinition.ALL_ROLES.size());

        if (!provisionOnStartup) {
            log.info("Role provisioning skipped (tnt.roles.provision-on-startup=false) — " +
                     "suitable for test profiles");
            logLocalRoleDefinitions();
            return;
        }

        if (tntRoleInitializationService != null) {
            delegateToTntRolesCoreProvisioning();
        } else {
            log.warn("TntRoleInitializationService not found on classpath — " +
                     "tnt-roles-core is absent. Falling back to local role definition logging.");
            logLocalRoleDefinitions();
        }
    }

    /**
     * Delegates role provisioning to {@code TntRoleInitializationService.provisionSystemRoles()}.
     * Calls the method via reflection to avoid a hard compile-time dependency.
     */
    private void delegateToTntRolesCoreProvisioning() {
        try {
            // Attempt to call provisionSystemRoles() — idempotent, safe to call at every startup
            Method provisionMethod = tntRoleInitializationService.getClass()
                    .getMethod("provisionSystemRoles");
            Object result = provisionMethod.invoke(tntRoleInitializationService);

            // If the return value is a reactive Publisher, block for startup feedback
            if (result != null && result.getClass().getName().contains("Mono")) {
                Method blockMethod = result.getClass().getMethod("block");
                blockMethod.invoke(result);
            }

            log.info("TiiBnTick role provisioning completed via tnt-roles-core — " +
                     "{} roles provisioned for system tenant [{}]",
                    TntRoleDefinition.ALL_ROLES.size(), systemTenantId);

        } catch (NoSuchMethodException e) {
            // TntRoleInitializationService is present but API differs — log and continue
            log.warn("TntRoleInitializationService.provisionSystemRoles() not found (API mismatch). " +
                     "Roles will be provisioned asynchronously on ApplicationReadyEvent.");
            logLocalRoleDefinitions();
        } catch (Exception e) {
            // Non-fatal: provisioning will retry on ApplicationReadyEvent
            log.warn("TiiBnTick role provisioning via tnt-roles-core failed at startup step 5: {}. " +
                     "TntRoleInitializationService will retry on ApplicationReadyEvent.",
                    e.getMessage());
            logLocalRoleDefinitions();
        }
    }

    /**
     * Logs the local {@link TntRoleDefinition#ALL_ROLES} definitions for visibility.
     * Used as fallback when tnt-roles-core is absent or provisioning is skipped.
     */
    private void logLocalRoleDefinitions() {
        log.info("TiiBnTick local role definitions ({} roles):", TntRoleDefinition.ALL_ROLES.size());
        for (TntRoleDefinition role : TntRoleDefinition.ALL_ROLES) {
            log.info("  → [{}] {} — scope: {} — {} permissions{}",
                    role.getRoleCode(),
                    role.getDisplayName(),
                    role.getScopeType(),
                    role.getPermissions().size(),
                    role.isSystemRole() ? " [SYSTEM]" : "");
        }
    }
}
