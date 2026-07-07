package com.yowyob.tiibntick.bootstrap.startup;

import lombok.Getter;

import java.util.List;

/**
 * Defines the 9 ordered startup steps executed by {@link TntStartupRunner}
 * after the Spring context has been fully initialized.
 *
 * <p>Mandatory steps ({@code mandatory=true}) cause the application to enter
 * {@link StartupStatus#FAILED} if they fail. Optional steps degrade to
 * {@link StartupStatus#DEGRADED}.
 *
 * <p> — Updated step descriptions:
 * <ul>
 *   <li>Step 5 (RoleRegistration): now delegates to {@code TntRoleInitializationService}
 *       from {@code tnt-roles-core} for idempotent provisioning of the 9 canonical
 *       {@code TntRole} definitions. Falls back to local role definition logging
 *       when {@code tnt-roles-core} is absent.</li>
 *   <li>Step 7 (KafkaTopicsCreation): topic count updated to 45 (v2.1: 33) to include
 *       the 12 new {@code tnt.incident.*} topics and 2 enriched realtime topics
 *       ({@code tnt.realtime.gps.position.updated}, {@code tnt.realtime.geofence.triggered})
 *       introduced for {@code tnt-incident-core}.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Getter
public class TntStartupSequence {

    private final List<StartupStep> steps = List.of(
            new StartupStep(1, "ModuleRegistry",
                    "Register all 37 assembled TiiBnTick Core modules (L0-L5,  incl. tnt-billing-templates)", true),
            new StartupStep(2, "KernelPing",
                    "Ping the Yowyob Kernel (RT-comops) and retrieve kernel version", false),
            new StartupStep(3, "YowAuthCheck",
                    "Verify comops-auth-core (YowAuth0) is reachable — validates tnt-auth-core config", false),
            new StartupStep(4, "KernelEventBusCheck",
                    "Verify yow-event-kernel Kafka bridge is operational", false),
            new StartupStep(5, "RoleRegistration",
                    "Provision 9 canonical TntRole definitions via tnt-roles-core " +
                    "(TntRoleInitializationService, idempotent, system tenant)", true),
            new StartupStep(6, "SettingsRegistration",
                    "Register tnt.* settings keys in yow-settings-kernel", true),
            new StartupStep(7, "KafkaTopicsCreation",
                    "Ensure all 58 TNT Kafka topics exist on the broker. " +
                    " additions: 12 tnt.incident.* topics, " +
                    "12 tnt.freelancer_org/admin/billing/vehicle.* topics for FreelancerOrg model", true),
            new StartupStep(8, "ExtensionRegistry",
                    "Register TiiBnTick extensions (actor, org, tp, notify, media, incident)", true),
            new StartupStep(9, "DatabasePyramidCheck",
                    "Verify PostgreSQL connectivity for all DB levels in the pyramid " +
                    "(includes tnt_incident schema, tnt_geo schema)", true),
            new StartupStep(10, "FreelancerOrgInit",
                    "Initialize FreelancerOrg platform configuration: " +
                    "validate tnt.freelancer-org.* properties, verify billing DSL limits, " +
                    "confirm tnt-billing-templates module is active", false)
    );

    public StartupStep getStep(int order) {
        return steps.stream()
                .filter(s -> s.getOrder() == order)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No startup step with order: " + order));
    }

    public boolean hasAnyCriticalFailure() {
        return steps.stream().anyMatch(StartupStep::isCriticalFailure);
    }

    public boolean hasAnyOptionalFailure() {
        return steps.stream()
                .filter(s -> !s.isMandatory())
                .anyMatch(s -> s.getStatus() == StepStatus.FAILED);
    }
}
