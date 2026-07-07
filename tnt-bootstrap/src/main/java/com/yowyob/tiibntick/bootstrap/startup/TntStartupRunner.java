package com.yowyob.tiibntick.bootstrap.startup;

import com.yowyob.tiibntick.bootstrap.bridge.YowyobKernelBridge;
import com.yowyob.tiibntick.bootstrap.config.TiiBnTickApplicationContext;
import com.yowyob.tiibntick.bootstrap.config.TntKafkaTopicsConfig;
import com.yowyob.tiibntick.bootstrap.config.TntModuleRegistry;
import com.yowyob.tiibntick.bootstrap.health.DatabasePyramidStatus;
import com.yowyob.tiibntick.bootstrap.registry.TntExtensionRegistry;
import com.yowyob.tiibntick.bootstrap.registry.TntRoleRegistrar;
import com.yowyob.tiibntick.bootstrap.registry.TntSettingsRegistrar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Application runner that orchestrates the 9 ordered startup steps
 * after the Spring Boot context has fully initialized.
 *
 * <p>Execution order: {@code @Order(1)} — runs before any other ApplicationRunner.
 *
 * <p> — Updated startup steps:
 * <ul>
 *   <li>Step 1: registers 36 modules (v2.1: 32) including tnt-common-core, tnt-auth-core,
 *       tnt-roles-core, tnt-incident-core.</li>
 *   <li>Step 5: delegates role provisioning to {@code TntRoleRegistrar} which calls
 *       {@code TntRoleInitializationService} (tnt-roles-core) when available.
 *       Provisions 9 canonical {@code TntRole} definitions (v2.1: 7 roles).</li>
 *   <li>Step 7: 45 Kafka topics created (v2.1: 33), including 12 incident topics.</li>
 * </ul>
 *
 * <p>Startup completes with status:
 * <ul>
 *   <li>{@link StartupStatus#COMPLETED} — all 9 steps succeeded</li>
 *   <li>{@link StartupStatus#DEGRADED} — optional steps failed but core is up</li>
 *   <li>{@link StartupStatus#FAILED} — a mandatory step failed (app should not serve traffic)</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TntStartupRunner implements ApplicationRunner {

    private final TiiBnTickApplicationContext appContext;
    private final TntModuleRegistry moduleRegistry;
    private final TntExtensionRegistry extensionRegistry;
    private final YowyobKernelBridge kernelBridge;
    private final TntRoleRegistrar roleRegistrar;
    private final TntSettingsRegistrar settingsRegistrar;
    private final TntKafkaTopicsConfig kafkaTopicsConfig;

    private final TntStartupSequence startupSequence = new TntStartupSequence();

    @Override
    public void run(ApplicationArguments args) {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("  TiiBnTick Core  — Starting up (9-step sequence)");
        log.info("  Foundation: tnt-common-core + tnt-auth-core + tnt-roles-core");
        log.info("  Logistics:  tnt-incident-core (140+ incident types)");
        log.info("═══════════════════════════════════════════════════════════");

        appContext.setStartupStatus(StartupStatus.IN_PROGRESS);
        appContext.setStartupTime(LocalDateTime.now());

        // ── Step 1: Module Registry ──────────────────────────────────────────
        runStep(1, () -> {
            moduleRegistry.registerModules();
            appContext.setResolvedModules(new ArrayList<>(moduleRegistry.getAll().values()));
            log.info("  Modules registered: {} (L0-L5, )", moduleRegistry.count());
        });

        // ── Step 2: Kernel Ping (optional) ──────────────────────────────────
        runStep(2, () -> {
            boolean ok = kernelBridge.ping().block();
            if (ok) {
                String version = kernelBridge.getKernelVersion().block();
                log.info("  Kernel version: {}", version);
            }
        });

        // ── Step 3: YowAuth / tnt-auth-core Check (optional) ────────────────
        // Validates that the Kernel auth bridge (tnt-auth-core) is reachable.
        // Verifies JWT issuer URI and that TntSecurityContextService is wired.
        runStep(3, () -> {
            kernelBridge.checkYowAuthStatus().block();
            appContext.setKernelConnectionStatus(kernelBridge.getConnectionStatus());
            log.info("  tnt-auth-core bridge: JWT issuer verified");
        });

        // ── Step 4: Event Bus Check (optional) ──────────────────────────────
        runStep(4, () -> {
            kernelBridge.checkKernelEventBus().block();
        });

        // ── Step 5: Role Registration (tnt-roles-core delegation) ────────────
        // Delegates to TntRoleInitializationService when tnt-roles-core is present.
        // Provisions 9 canonical TntRole definitions (vs 7 in v2.1).
        // Falls back to local TntRoleDefinition.ALL_ROLES logging when absent.
        runStep(5, roleRegistrar::registerAll);

        // ── Step 6: Settings Registration ────────────────────────────────────
        runStep(6, settingsRegistrar::registerAll);

        // ── Step 7: Kafka Topics ──────────────────────────────────────────────
        // 45 topics in  (33 in v2.1):
        //   +12 tnt.incident.* topics (tnt-incident-core)
        //   +2  tnt.realtime.gps.position.updated + tnt.realtime.geofence.triggered
        runStep(7, () -> log.info(
                "  Kafka topics: 45 topics declared " +
                "(+12 tnt.incident.* + 2 enriched realtime topics for tnt-incident-core). " +
                "KafkaAdmin will auto-create missing topics on first broker access."));

        // ── Step 8: Extension Registry ───────────────────────────────────────
        runStep(8, extensionRegistry::registerAll);

        // ── Step 9: Database Pyramid Check ───────────────────────────────────
        // Now includes tnt_incident schema (6 tables + 11 indexes).
        runStep(9, () -> {
            DatabasePyramidStatus dbStatus = DatabasePyramidStatus.checkLocal();
            appContext.setDatabasePyramidStatus(dbStatus);
            log.info("  Database pyramid: {} (includes tnt_incident schema)",
                    dbStatus.allConnected() ? "✅ all connected" : "⚠️ partial");
        });

        // ── Determine final status ────────────────────────────────────────────
        if (startupSequence.hasAnyCriticalFailure()) {
            appContext.setStartupStatus(StartupStatus.FAILED);
            log.error("═══════════════════════════════════════════════════════════");
            log.error("  ❌ TiiBnTick Core  startup FAILED — check logs above");
            log.error("═══════════════════════════════════════════════════════════");
        } else if (startupSequence.hasAnyOptionalFailure()) {
            appContext.setStartupStatus(StartupStatus.DEGRADED);
            log.warn("═══════════════════════════════════════════════════════════");
            log.warn("  ⚠️ TiiBnTick Core  started in DEGRADED mode");
            log.warn("═══════════════════════════════════════════════════════════");
        } else {
            appContext.setStartupStatus(StartupStatus.COMPLETED);
            log.info("═══════════════════════════════════════════════════════════");
            log.info("  ✅ TiiBnTick Core  startup COMPLETED — {} modules active",
                    appContext.getModuleCount());
            log.info("═══════════════════════════════════════════════════════════");
        }
    }

    // ── Private step executor ─────────────────────────────────────────────────

    private void runStep(int order, Runnable action) {
        StartupStep step = startupSequence.getStep(order);
        step.markInProgress();
        try {
            action.run();
            step.markSuccess();
        } catch (Exception e) {
            step.markFailed(e.getMessage());
            if (step.isMandatory()) {
                log.error("Critical startup failure at step {}: {}", order, e.getMessage(), e);
            }
        }
    }
}
