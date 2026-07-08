package com.yowyob.tiibntick.core.roles.domain.model;

import java.util.Set;

import static com.yowyob.tiibntick.core.roles.domain.model.TntPermission.*;

/**
 * Canonical enumeration of all TiiBnTick business roles.
 *
 * <p>Each entry carries:
 * <ul>
 *   <li>{@code code} — unique string identifier stored in the Kernel DB (Role.code)</li>
 *   <li>{@code label} — human-readable display name</li>
 *   <li>{@code scopeType} — Kernel scope this role must be assigned within</li>
 *   <li>{@code defaultPermissions} — canonical permission set granted to this role</li>
 *   <li>{@code systemRole} — true if this role cannot be deleted or modified by tenants</li>
 * </ul>
 *
 * <p>These definitions are the authoritative source used by:
 * <ul>
 *   <li>{@link com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry}
 *       to expose role definitions to other modules</li>
 *   <li>{@code tnt-administration-core} to provision default roles when a new tenant is created</li>
 *   <li>{@link com.yowyob.tiibntick.core.roles.application.service.TntRoleInitializationService}
 *       to verify/seed system-level roles at startup</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public enum TntRole {

    /**
     * Manages a full agency: staff, missions, billing, reports.
     * Scope: per-agency.
     */
    AGENCY_MANAGER(
            "AGENCY_MANAGER",
            "Agency Manager",
            RoleScopeType.AGENCY,
            true,
            Set.of(
                    MISSION_CREATE, MISSION_READ, MISSION_ASSIGN, MISSION_REASSIGN, MISSION_CANCEL,
                    DELIVERY_READ, DELIVERY_TRACK, DELIVERY_RETURN,
                    AGENCY_READ, AGENCY_WRITE, AGENCY_MANAGE,
                    BRANCH_READ, BRANCH_WRITE, BRANCH_MANAGE,
                    ACTOR_READ, ACTOR_WRITE, ACTOR_APPROVE, ACTOR_SUSPEND,
                    BILLING_READ, BILLING_WRITE, BILLING_POST,
                    INVOICE_READ, INVOICE_ISSUE,
                    WALLET_READ,
                    REPORT_READ, REPORT_EXPORT,
                    ANNOUNCEMENT_READ, ANNOUNCEMENT_RESPOND, ANNOUNCEMENT_ELECT,
                    DISPUTE_READ, DISPUTE_RESOLVE,
                    TRUST_READ, TRUST_VERIFY,
                    MEDIA_READ, MEDIA_UPLOAD,
                    RESOURCE_READ, RESOURCE_WRITE, RESOURCE_RESERVE,
                    PRODUCT_READ, PRODUCT_WRITE,
                    INVENTORY_READ, INVENTORY_WRITE,
                    SETTINGS_READ, SETTINGS_WRITE,
                    ROUTE_READ, ROUTE_COMPUTE,
                    GEO_READ,
                    ADMIN_ROLES, ADMIN_USERS, ADMIN_AUDIT
            )
    ),

    /**
     * Manages a branch (antenne) under an agency: daily operations, local staff.
     * Scope: per-agency (branch-level context managed in application).
     */
    BRANCH_MANAGER(
            "BRANCH_MANAGER",
            "Branch Manager",
            RoleScopeType.AGENCY,
            true,
            Set.of(
                    MISSION_CREATE, MISSION_READ, MISSION_ASSIGN, MISSION_REASSIGN, MISSION_CANCEL,
                    DELIVERY_READ, DELIVERY_TRACK,
                    AGENCY_READ,
                    BRANCH_READ, BRANCH_WRITE, BRANCH_MANAGE,
                    ACTOR_READ, ACTOR_WRITE,
                    BILLING_READ,
                    INVOICE_READ,
                    REPORT_READ,
                    ANNOUNCEMENT_READ, ANNOUNCEMENT_RESPOND,
                    DISPUTE_READ,
                    TRUST_READ,
                    MEDIA_READ, MEDIA_UPLOAD,
                    RESOURCE_READ,
                    GEO_READ
            )
    ),

    /**
     * Salaried deliverer permanently attached to an agency.
     * Can start and complete missions assigned to them.
     * Scope: per-agency.
     */
    PERMANENT_DELIVERER(
            "PERMANENT_DELIVERER",
            "Permanent Deliverer",
            RoleScopeType.AGENCY,
            true,
            Set.of(
                    MISSION_READ, MISSION_START, MISSION_COMPLETE,
                    DELIVERY_READ, DELIVERY_TRACK, DELIVERY_CONFIRM, DELIVERY_PROOF,
                    ACTOR_READ,
                    WALLET_READ,
                    TRUST_READ, TRUST_VERIFY,
                    MEDIA_READ, MEDIA_UPLOAD,
                    GEO_READ, ROUTE_READ,
                    DISPUTE_CREATE
            )
    ),

    /**
     * Independent deliverer (auto-entrepreneur) who responds to announcements
     * and may be temporarily associated with agencies.
     * Scope: tenant (cross-agency).
     */
    FREELANCER(
            "FREELANCER",
            "Freelancer Deliverer",
            RoleScopeType.TENANT,
            true,
            Set.of(
                    MISSION_READ, MISSION_START, MISSION_COMPLETE,
                    DELIVERY_READ, DELIVERY_TRACK, DELIVERY_CONFIRM, DELIVERY_PROOF,
                    ANNOUNCEMENT_READ, ANNOUNCEMENT_RESPOND,
                    ACTOR_READ,
                    WALLET_READ, WALLET_WRITE,
                    PAYMENT_PROCESS,
                    TRUST_READ, TRUST_VERIFY,
                    MEDIA_READ, MEDIA_UPLOAD,
                    GEO_READ, ROUTE_READ,
                    DISPUTE_CREATE
            )
    ),

    /**
     * Operates a relay point (hub): receives, stores, and hands off parcels.
     * Scope: per-agency (linked to a specific hub).
     */
    RELAY_OPERATOR(
            "RELAY_OPERATOR",
            "Relay Point Operator",
            RoleScopeType.AGENCY,
            true,
            Set.of(
                    DELIVERY_READ, DELIVERY_TRACK, DELIVERY_CONFIRM, DELIVERY_PROOF,
                    RELAY_READ, RELAY_WRITE, RELAY_OPERATE,
                    MISSION_READ,
                    TRUST_READ, TRUST_VERIFY, TRUST_ANCHOR,
                    MEDIA_READ, MEDIA_UPLOAD,
                    GEO_READ,
                    DISPUTE_CREATE
            )
    ),

    /**
     * End client: creates delivery announcements, tracks parcels, processes payments.
     * Scope: tenant (cross-agency — a client can use multiple agencies).
     */
    CLIENT(
            "CLIENT",
            "Client / Sender",
            RoleScopeType.TENANT,
            true,
            Set.of(
                    ANNOUNCEMENT_CREATE, ANNOUNCEMENT_READ, ANNOUNCEMENT_UPDATE,
                    ANNOUNCEMENT_DELETE, ANNOUNCEMENT_ELECT,
                    DELIVERY_READ, DELIVERY_TRACK,
                    BILLING_READ,
                    INVOICE_READ,
                    WALLET_READ, WALLET_WRITE,
                    PAYMENT_PROCESS,
                    TRUST_READ, TRUST_VERIFY,
                    MEDIA_READ,
                    DISPUTE_CREATE, DISPUTE_READ
            )
    ),

    /**
     * Customer support agent: read-only across most modules, can resolve disputes.
     * Scope: tenant.
     */
    SUPPORT_AGENT(
            "SUPPORT_AGENT",
            "Support Agent",
            RoleScopeType.TENANT,
            true,
            Set.of(
                    MISSION_READ,
                    DELIVERY_READ, DELIVERY_TRACK,
                    ACTOR_READ,
                    AGENCY_READ,
                    BILLING_READ,
                    INVOICE_READ,
                    REPORT_READ,
                    TRUST_READ, TRUST_VERIFY,
                    DISPUTE_READ, DISPUTE_RESOLVE,
                    ADMIN_AUDIT,
                    MEDIA_READ
            )
    ),

    /**
     * Organization-level administrator: manages multiple agencies under a conglomerate.
     * Scope: organization.
     */
    ORG_ADMIN(
            "ORG_ADMIN",
            "Organization Administrator",
            RoleScopeType.ORGANIZATION,
            true,
            Set.of(
                    MISSION_READ,
                    DELIVERY_READ, DELIVERY_TRACK,
                    AGENCY_READ, AGENCY_WRITE, AGENCY_MANAGE,
                    BRANCH_READ,
                    ACTOR_READ, ACTOR_WRITE, ACTOR_APPROVE, ACTOR_SUSPEND,
                    BILLING_READ, BILLING_WRITE,
                    INVOICE_READ,
                    REPORT_READ, REPORT_EXPORT,
                    DISPUTE_READ, DISPUTE_RESOLVE,
                    TRUST_READ, TRUST_VERIFY,
                    PRODUCT_READ, PRODUCT_WRITE,
                    INVENTORY_READ, INVENTORY_WRITE,
                    RESOURCE_READ, RESOURCE_WRITE,
                    SETTINGS_READ, SETTINGS_WRITE,
                    ADMIN_ROLES, ADMIN_USERS, ADMIN_AUDIT, ADMIN_SETTINGS, ADMIN_GOVERN,
                    ORG_ADMIN_PERM
            )
    ),

    /**
     * System-level TiiBnTick platform administrator.
     * Has ALL permissions. Scope: system. Cannot be assigned to normal users.
     */
    TNT_ADMIN(
            "TNT_ADMIN",
            "TiiBnTick System Administrator",
            RoleScopeType.SYSTEM,
            true,
            Set.of(ALL)
    );

    private final String code;
    private final String label;
    private final RoleScopeType scopeType;
    private final boolean systemRole;
    private final Set<String> defaultPermissions;

    TntRole(String code, String label, RoleScopeType scopeType, boolean systemRole, Set<String> defaultPermissions) {
        this.code = code;
        this.label = label;
        this.scopeType = scopeType;
        this.systemRole = systemRole;
        this.defaultPermissions = Set.copyOf(defaultPermissions);
    }

    public String code() { return code; }
    public String label() { return label; }
    public RoleScopeType scopeType() { return scopeType; }
    public boolean isSystemRole() { return systemRole; }
    public Set<String> defaultPermissions() { return defaultPermissions; }

    /**
     * Finds a TntRole by its code string.
     *
     * @throws IllegalArgumentException if the code is not a known TiiBnTick role
     */
    public static TntRole fromCode(String code) {
        for (TntRole role : values()) {
            if (role.code.equalsIgnoreCase(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown TiiBnTick role code: " + code);
    }

    /**
     * Returns true if the given code corresponds to a known TiiBnTick role.
     */
    public static boolean isKnownRole(String code) {
        for (TntRole role : values()) {
            if (role.code.equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }
}
