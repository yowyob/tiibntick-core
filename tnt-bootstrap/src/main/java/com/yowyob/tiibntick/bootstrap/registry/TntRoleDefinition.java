package com.yowyob.tiibntick.bootstrap.registry;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Value object describing a TiiBnTick-specific RBAC role.
 *
 * <p><strong> — Alignment with {@code TntRole} enum (tnt-roles-core):</strong><br>
 * This class now mirrors the 9 canonical roles defined in
 * {@code com.yowyob.tiibntick.core.roles.domain.model.TntRole}.
 * When {@code tnt-roles-core} is on the classpath, {@link TntRoleRegistrar} delegates
 * role provisioning to {@code TntRoleInitializationService} which uses the
 * authoritative {@code TntRole} definitions. This local class is retained for:
 * <ul>
 *   <li>Module registry reporting ({@link com.yowyob.tiibntick.bootstrap.config.TntModuleRegistry})</li>
 *   <li>Fallback logging when tnt-roles-core is absent</li>
 *   <li>Documentation of permissions assigned to each role</li>
 * </ul>
 *
 * @author MANFOUO Braun
 * @deprecated Use {@code TntRole} from {@code tnt-roles-core} for actual provisioning.
 *             This class is kept for bootstrap-level reporting only.
 */
@Getter
@Builder
public final class TntRoleDefinition {

    private final String roleCode;
    private final String displayName;
    private final String description;
    private final List<String> permissions;
    private final boolean systemRole;
    /** Scope level: SYSTEM, ORGANIZATION, AGENCY, or TENANT. */
    private final String scopeType;

    // ── 9 TiiBnTick canonical roles — aligned with TntRole enum ──────────────

    /** Full agency management: staff, missions, billing, reports. Maps to TntRole.AGENCY_MANAGER. */
    public static final TntRoleDefinition AGENCY_MANAGER = TntRoleDefinition.builder()
            .roleCode("AGENCY_MANAGER")
            .displayName("Agency Manager")
            .description("Full agency management: staff, missions, billing, and reports")
            .permissions(List.of(
                    "agency:read", "agency:write", "agency:manage",
                    "branch:read", "branch:write", "branch:manage",
                    "mission:create", "mission:assign", "mission:start", "mission:complete",
                    "actor:read", "actor:write", "actor:approve", "actor:suspend",
                    "billing:read", "billing:write",
                    "invoice:read", "invoice:issue",
                    "report:read", "report:export",
                    "dispute:create", "dispute:read", "dispute:resolve",
                    "delivery:read", "delivery:track", "delivery:confirm"))
            .systemRole(false)
            .scopeType("AGENCY")
            .build();

    /** Daily operations of an antenne (branch). Maps to TntRole.BRANCH_MANAGER. */
    public static final TntRoleDefinition BRANCH_MANAGER = TntRoleDefinition.builder()
            .roleCode("BRANCH_MANAGER")
            .displayName("Branch Manager")
            .description("Daily operations of an antenne (branch)")
            .permissions(List.of(
                    "branch:read", "branch:write",
                    "mission:create", "mission:assign",
                    "delivery:read", "delivery:track",
                    "actor:read",
                    "relay:read", "relay:write", "relay:operate",
                    "report:read"))
            .systemRole(false)
            .scopeType("AGENCY")
            .build();

    /** Salaried deliverer attached to an agency. Maps to TntRole.PERMANENT_DELIVERER. */
    public static final TntRoleDefinition PERMANENT_DELIVERER = TntRoleDefinition.builder()
            .roleCode("PERMANENT_DELIVERER")
            .displayName("Permanent Deliverer")
            .description("Salaried deliverer attached to an agency")
            .permissions(List.of(
                    "mission:create", "mission:start", "mission:complete",
                    "delivery:read", "delivery:track", "delivery:confirm", "delivery:proof",
                    "wallet:read",
                    "media:read", "media:upload"))
            .systemRole(false)
            .scopeType("AGENCY")
            .build();

    /** Independent deliverer responding to announcements. Maps to TntRole.FREELANCER. */
    public static final TntRoleDefinition FREELANCER = TntRoleDefinition.builder()
            .roleCode("FREELANCER")
            .displayName("Freelancer Deliverer")
            .description("Independent deliverer responding to client announcements")
            .permissions(List.of(
                    "announcement:read", "announcement:respond",
                    "mission:start", "mission:complete",
                    "delivery:read", "delivery:confirm", "delivery:proof",
                    "wallet:read",
                    "media:read", "media:upload"))
            .systemRole(false)
            .scopeType("TENANT")
            .build();

    /** Hub/relay point operator. Maps to TntRole.RELAY_OPERATOR. */
    public static final TntRoleDefinition RELAY_OPERATOR = TntRoleDefinition.builder()
            .roleCode("RELAY_OPERATOR")
            .displayName("Relay Point Operator")
            .description("Manages a hub/relay point: receives and hands out parcels")
            .permissions(List.of(
                    "relay:read", "relay:write", "relay:operate",
                    "delivery:read", "delivery:track",
                    "media:read", "media:upload",
                    "report:read"))
            .systemRole(false)
            .scopeType("AGENCY")
            .build();

    /** End client — announces, tracks, pays. Maps to TntRole.CLIENT. */
    public static final TntRoleDefinition CLIENT = TntRoleDefinition.builder()
            .roleCode("CLIENT")
            .displayName("Client")
            .description("End client who announces delivery needs, tracks parcels, and pays")
            .permissions(List.of(
                    "announcement:create",
                    "delivery:read", "delivery:track",
                    "wallet:read", "wallet:write", "payment:process",
                    "invoice:read",
                    "dispute:create", "dispute:read",
                    "media:read"))
            .systemRole(false)
            .scopeType("TENANT")
            .build();

    /** Customer support — read-only + dispute resolution. Maps to TntRole.SUPPORT_AGENT. */
    public static final TntRoleDefinition SUPPORT_AGENT = TntRoleDefinition.builder()
            .roleCode("SUPPORT_AGENT")
            .displayName("Support Agent")
            .description("Customer support: read-only access with dispute resolution capability")
            .permissions(List.of(
                    "delivery:read", "delivery:track",
                    "actor:read",
                    "dispute:read", "dispute:resolve",
                    "billing:read",
                    "report:read",
                    "trust:read", "trust:verify"))
            .systemRole(false)
            .scopeType("TENANT")
            .build();

    /** Multi-agency organization administrator. Maps to TntRole.ORG_ADMIN. */
    public static final TntRoleDefinition ORG_ADMIN = TntRoleDefinition.builder()
            .roleCode("ORG_ADMIN")
            .displayName("Organization Admin")
            .description("Multi-agency organization administrator")
            .permissions(List.of(
                    "org:admin",
                    "agency:read", "agency:write", "agency:manage",
                    "actor:read", "actor:write",
                    "billing:read", "billing:write",
                    "report:read", "report:export",
                    "admin:roles", "admin:users", "admin:audit"))
            .systemRole(false)
            .scopeType("ORGANIZATION")
            .build();

    /** Platform-wide super-admin (wildcard '*' permission). Maps to TntRole.TNT_ADMIN. */
    public static final TntRoleDefinition TNT_ADMIN = TntRoleDefinition.builder()
            .roleCode("TNT_ADMIN")
            .displayName("TiiBnTick Super Admin")
            .description("Platform-wide super-admin with wildcard '*' permission on all resources")
            .permissions(List.of("*"))
            .systemRole(true)
            .scopeType("SYSTEM")
            .build();

    /** All 9 canonical TiiBnTick roles — aligned with {@code TntRole} enum. */
    public static final List<TntRoleDefinition> ALL_ROLES = List.of(
            AGENCY_MANAGER, BRANCH_MANAGER, PERMANENT_DELIVERER,
            FREELANCER, RELAY_OPERATOR, CLIENT,
            SUPPORT_AGENT, ORG_ADMIN, TNT_ADMIN);
}
