package com.yowyob.tiibntick.core.roles.domain.model;

/**
 * Centralized catalog of all TiiBnTick granular permission strings.
 *
 * <p>Format: {@code resource:action}. When assigned through the Kernel's
 * {@code RolesPermissionResolver}, permissions are stored in the JWT with a
 * scope suffix appended by the Kernel: {@code resource:action#AGENCY:<id>}.
 * The evaluator in {@link com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator}
 * handles both bare and scoped variants transparently.
 *
 * <p>All consumers must import from here — never hardcode permission strings inline.
 *
 * @author MANFOUO Braun
 */
public final class TntPermission {

    private TntPermission() {}

    // ─────────────────────────────────────────────────────────────
    // MISSION — Core logistics unit (a delivery job)
    // ─────────────────────────────────────────────────────────────

    public static final String MISSION_CREATE   = "mission:create";
    public static final String MISSION_READ     = "mission:read";
    public static final String MISSION_ASSIGN   = "mission:assign";
    public static final String MISSION_REASSIGN = "mission:reassign";
    public static final String MISSION_CANCEL   = "mission:cancel";
    public static final String MISSION_START    = "mission:start";
    public static final String MISSION_COMPLETE = "mission:complete";
    public static final String MISSION_DISPUTE  = "mission:dispute";

    // ─────────────────────────────────────────────────────────────
    // DELIVERY — Physical parcel lifecycle
    // ─────────────────────────────────────────────────────────────

    public static final String DELIVERY_READ    = "delivery:read";
    public static final String DELIVERY_TRACK   = "delivery:track";
    public static final String DELIVERY_CONFIRM = "delivery:confirm";
    public static final String DELIVERY_RETURN  = "delivery:return";
    public static final String DELIVERY_PROOF   = "delivery:proof";

    // ─────────────────────────────────────────────────────────────
    // ANNOUNCEMENT — TiiBnPick delivery request announcements
    // ─────────────────────────────────────────────────────────────

    public static final String ANNOUNCEMENT_CREATE  = "announcement:create";
    public static final String ANNOUNCEMENT_READ    = "announcement:read";
    public static final String ANNOUNCEMENT_UPDATE  = "announcement:update";
    public static final String ANNOUNCEMENT_DELETE  = "announcement:delete";
    public static final String ANNOUNCEMENT_RESPOND = "announcement:respond";
    public static final String ANNOUNCEMENT_ELECT   = "announcement:elect";

    // ─────────────────────────────────────────────────────────────
    // AGENCY — Agency and branch management
    // ─────────────────────────────────────────────────────────────

    public static final String AGENCY_READ   = "agency:read";
    public static final String AGENCY_WRITE  = "agency:write";
    public static final String AGENCY_MANAGE = "agency:manage";
    public static final String BRANCH_READ   = "branch:read";
    public static final String BRANCH_WRITE  = "branch:write";
    public static final String BRANCH_MANAGE = "branch:manage";

    // ─────────────────────────────────────────────────────────────
    // ACTOR — Deliverer and freelancer profiles
    // ─────────────────────────────────────────────────────────────

    public static final String ACTOR_READ    = "actor:read";
    public static final String ACTOR_WRITE   = "actor:write";
    public static final String ACTOR_APPROVE = "actor:approve";
    public static final String ACTOR_SUSPEND = "actor:suspend";

    // ─────────────────────────────────────────────────────────────
    // ROUTE & GEO — Route optimization and geolocation
    // ─────────────────────────────────────────────────────────────

    public static final String ROUTE_READ    = "route:read";
    public static final String ROUTE_COMPUTE = "route:compute";
    public static final String GEO_READ      = "geo:read";

    // ─────────────────────────────────────────────────────────────
    // RELAY — Relay point / hub operations
    // ─────────────────────────────────────────────────────────────

    public static final String RELAY_READ    = "relay:read";
    public static final String RELAY_WRITE   = "relay:write";
    public static final String RELAY_OPERATE = "relay:operate";

    // ─────────────────────────────────────────────────────────────
    // BILLING — Invoices, pricing and billing engine
    // ─────────────────────────────────────────────────────────────

    public static final String BILLING_READ  = "billing:read";
    public static final String BILLING_WRITE = "billing:write";
    public static final String BILLING_POST  = "billing:post";
    public static final String INVOICE_READ  = "invoice:read";
    public static final String INVOICE_ISSUE = "invoice:issue";

    // ─────────────────────────────────────────────────────────────
    // WALLET — Mobile money and in-app wallet
    // ─────────────────────────────────────────────────────────────

    public static final String WALLET_READ    = "wallet:read";
    public static final String WALLET_WRITE   = "wallet:write";
    public static final String PAYMENT_PROCESS = "payment:process";
    public static final String PAYMENT_REFUND  = "payment:refund";

    // ─────────────────────────────────────────────────────────────
    // REPORT — Analytics and financial reports
    // ─────────────────────────────────────────────────────────────

    public static final String REPORT_READ   = "report:read";
    public static final String REPORT_EXPORT = "report:export";

    // ─────────────────────────────────────────────────────────────
    // TRUST — Blockchain proof and verification (TiiBnTick Trust)
    // ─────────────────────────────────────────────────────────────

    public static final String TRUST_READ   = "trust:read";
    public static final String TRUST_VERIFY = "trust:verify";
    public static final String TRUST_ANCHOR = "trust:anchor";

    // ─────────────────────────────────────────────────────────────
    // ADMIN — Administrative operations (used by tnt-administration-core)
    // ─────────────────────────────────────────────────────────────

    public static final String ADMIN_ROLES       = "admin:roles";
    public static final String ADMIN_USERS       = "admin:users";
    public static final String ADMIN_AUDIT       = "admin:audit";
    public static final String ADMIN_SETTINGS    = "admin:settings";
    public static final String ADMIN_GOVERN      = "admin:govern";

    // ─────────────────────────────────────────────────────────────
    // PLATFORM — Platform-client identity management (tnt-platform-gateway-core
    // admin API). Deliberately NOT granted to any TntRole's defaultPermissions
    // below (unlike ADMIN_ROLES/ADMIN_USERS/ADMIN_AUDIT, which AGENCY_MANAGER and
    // ORG_ADMIN also carry) — only TNT_ADMIN's "*" wildcard satisfies this, making
    // it exclusive to the system administrator by construction. See
    // docs/auth/platform-client-management-design.md §4.
    // ─────────────────────────────────────────────────────────────

    public static final String PLATFORM_CLIENTS_MANAGE = "platform:clients";

    // ─────────────────────────────────────────────────────────────
    // DISPUTE — Delivery dispute management
    // ─────────────────────────────────────────────────────────────

    public static final String DISPUTE_CREATE  = "dispute:create";
    public static final String DISPUTE_READ    = "dispute:read";
    public static final String DISPUTE_RESOLVE = "dispute:resolve";

    // ─────────────────────────────────────────────────────────────
    // MEDIA — QR codes, delivery proofs, PDF bordereaux
    // ─────────────────────────────────────────────────────────────

    public static final String MEDIA_READ   = "media:read";
    public static final String MEDIA_UPLOAD = "media:upload";
    public static final String MEDIA_DELETE = "media:delete";

    // ─────────────────────────────────────────────────────────────
    // SETTINGS — Tenant-level configuration
    // ─────────────────────────────────────────────────────────────

    public static final String SETTINGS_READ  = "settings:read";
    public static final String SETTINGS_WRITE = "settings:write";

    // ─────────────────────────────────────────────────────────────
    // PRODUCT / INVENTORY — Service offers and logistics inventory
    // ─────────────────────────────────────────────────────────────

    public static final String PRODUCT_READ    = "product:read";
    public static final String PRODUCT_WRITE   = "product:write";
    public static final String INVENTORY_READ  = "inventory:read";
    public static final String INVENTORY_WRITE = "inventory:write";

    // ─────────────────────────────────────────────────────────────
    // RESOURCE — Vehicles and logistics equipment
    // ─────────────────────────────────────────────────────────────

    public static final String RESOURCE_READ    = "resource:read";
    public static final String RESOURCE_WRITE   = "resource:write";
    public static final String RESOURCE_RESERVE = "resource:reserve";

    // ─────────────────────────────────────────────────────────────
    // SYSTEM — Super-admin / platform-wide
    // ─────────────────────────────────────────────────────────────

    public static final String SYSTEM_ADMIN  = "system:admin";
    public static final String TENANT_ADMIN  = "tenant:admin";
    public static final String ORG_ADMIN_PERM = "org:admin";

    /**
     * Wildcard — grants all permissions. Reserved for TNT_ADMIN system role only.
     */
    public static final String ALL = "*";
}
