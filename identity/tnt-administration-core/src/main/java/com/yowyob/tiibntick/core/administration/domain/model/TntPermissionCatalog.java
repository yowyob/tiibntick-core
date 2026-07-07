package com.yowyob.tiibntick.core.administration.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Defines the full TiiBnTick permission catalog.
 *
 * <p>All platform-specific permissions (delivery, blockchain, freelancer, point-relais, dispute,
 * and all permissions from the {@code TntPermission} constants in {@code tnt-roles-core}) are
 * registered here.
 *
 * <p> — Permission catalog enriched with all {@code TntPermission.*} entries from
 * {@code tnt-roles-core} (L1 Foundation). These follow the {@code resource:action[#SCOPE]}
 * naming convention used by {@code @RequirePermission} and {@code TntPermissionEvaluator}.
 * Existing entries use the legacy {@code module:action} format and are preserved for
 * backward compatibility.
 *
 * <p>The {@code kernelPermissionId} field is initially {@code null} for all entries — it is
 * populated at runtime by {@code KernelPermissionPort} when the Kernel reports a matching code.
 * TNT-exclusive system permissions (e.g., {@code tnt:blockchain:mine}) will always have a null
 * {@code kernelPermissionId} since they have no Kernel counterpart.
 *
 * <p>This catalog is built once at application startup and is immutable.
 *
 * @author MANFOUO Braun
 */
public final class TntPermissionCatalog {

    private TntPermissionCatalog() {}

    /** Protected system-level permissions that cannot be assigned via self-service. */
    public static final Set<String> TNT_PROTECTED = Set.of(
            "tnt:platform:admin",
            "tnt:blockchain:mine",
            "tnt:blockchain:validate",
            "system:admin",
            "tenant:admin"
    );

    /**
     * Builds the complete TiiBnTick permission catalog.
     *
     * <p>Includes both:
     * <ul>
     *   <li>Legacy {@code module:action} format entries (original tnt-administration-core catalog)</li>
     *   <li>New {@code resource:action} format entries from {@code TntPermission} (tnt-roles-core)</li>
     * </ul>
     *
     * <p>Key: permission code (lowercase, colon-separated).
     * The kernelPermissionId is left null here; it is resolved at runtime via
     * {@link com.yowyob.tiibntick.core.administration.application.port.out.KernelPermissionPort}.
     *
     * @return unmodifiable catalog map indexed by permission code
     */
    public static Map<String, TntPermissionEntry> buildCatalog() {
        Map<String, TntPermissionEntry> catalog = new LinkedHashMap<>();

        // ════════════════════════════════════════════════════════════════════════
        // SECTION 1: Legacy catalog (module:action format — original entries)
        // ════════════════════════════════════════════════════════════════════════

        // ── Delivery permissions ─────────────────────────────────────────────────
        add(catalog, "delivery:read",     "Delivery Read",     "Read delivery missions and tracking.",       "DELIVERY",     "AGENCY",        false, true);
        add(catalog, "delivery:write",    "Delivery Write",    "Create and manage delivery missions.",       "DELIVERY",     "AGENCY",        false, true);
        add(catalog, "delivery:dispatch", "Delivery Dispatch", "Dispatch parcels to couriers.",              "DELIVERY",     "AGENCY",        false, true);
        add(catalog, "delivery:track",    "Delivery Track",    "Real-time parcel tracking.",                 "DELIVERY",     "AGENCY",        false, true);
        add(catalog, "delivery:admin",    "Delivery Admin",    "Full delivery administration.",              "DELIVERY",     "ORGANIZATION",  false, true);

        // ── Freelancer (courier) permissions ──────────────────────────────────────
        add(catalog, "freelancer:read",         "Freelancer Read",    "View freelancer profiles.",                  "FREELANCER", "AGENCY",       false, true);
        add(catalog, "freelancer:write",        "Freelancer Write",   "Manage freelancer profiles.",                "FREELANCER", "ORGANIZATION", false, true);
        add(catalog, "freelancer:approve",      "Freelancer Approve", "Approve or reject freelancer applications.", "FREELANCER", "TENANT",       false, true);
        add(catalog, "freelancer:suspend",      "Freelancer Suspend", "Suspend a freelancer account.",              "FREELANCER", "ORGANIZATION", false, true);
        add(catalog, "freelancer:mission:take", "Mission Take",       "Accept and execute delivery missions.",      "FREELANCER", "AGENCY",       false, true);

        // ── Point Relais permissions ───────────────────────────────────────────────
        add(catalog, "point-relais:read",     "Point Relais Read",  "View relay point info.",                  "POINT_RELAIS", "AGENCY",       false, true);
        add(catalog, "point-relais:write",    "Point Relais Write", "Create and manage relay points.",         "POINT_RELAIS", "ORGANIZATION", false, true);
        add(catalog, "point-relais:receive",  "Parcel Receive",     "Receive parcels at relay point.",         "POINT_RELAIS", "AGENCY",       false, true);
        add(catalog, "point-relais:handover", "Parcel Handover",    "Hand over parcels to recipients.",        "POINT_RELAIS", "AGENCY",       false, true);

        // ── Blockchain / Trust permissions ────────────────────────────────────────
        add(catalog, "blockchain:wallet:read",      "Blockchain Wallet Read",   "Read blockchain wallets.",            "BLOCKCHAIN", "ORGANIZATION", false, true);
        add(catalog, "blockchain:wallet:create",    "Blockchain Wallet Create", "Create blockchain wallets.",          "BLOCKCHAIN", "ORGANIZATION", false, true);
        add(catalog, "blockchain:transaction:read", "Blockchain Tx Read",       "Read blockchain transactions.",       "BLOCKCHAIN", "ORGANIZATION", false, true);
        add(catalog, "blockchain:transaction:sign", "Blockchain Tx Sign",       "Build and sign blockchain payloads.", "BLOCKCHAIN", "ORGANIZATION", false, true);
        add(catalog, "blockchain:anchor:create",    "Blockchain Anchor Create", "Anchor documents on blockchain.",     "BLOCKCHAIN", "ORGANIZATION", false, true);
        add(catalog, "blockchain:block:read",       "Blockchain Block Read",    "Read blockchain blocks.",             "BLOCKCHAIN", "ORGANIZATION", false, true);
        add(catalog, "tnt:blockchain:mine",     "Blockchain Mine",     "Mine pending blocks (protected).",    "BLOCKCHAIN", "SYSTEM", true,  false);
        add(catalog, "tnt:blockchain:validate", "Blockchain Validate", "Validate blockchain integrity.",      "BLOCKCHAIN", "SYSTEM", true,  false);

        // ── Dispute / Litigation permissions ──────────────────────────────────────
        add(catalog, "dispute:read",    "Dispute Read",    "View disputes.",              "DISPUTE", "AGENCY",       false, true);
        add(catalog, "dispute:create",  "Dispute Create",  "File a new dispute.",         "DISPUTE", "AGENCY",       false, true);
        add(catalog, "dispute:resolve", "Dispute Resolve", "Resolve or close a dispute.", "DISPUTE", "ORGANIZATION", false, true);
        add(catalog, "dispute:admin",   "Dispute Admin",   "Full dispute management.",    "DISPUTE", "TENANT",       false, true);

        // ── Announcement / TiiBnPick permissions ──────────────────────────────────
        add(catalog, "announcement:read",    "Announcement Read",    "Read delivery announcements.",         "ANNOUNCEMENT", "AGENCY", false, true);
        add(catalog, "announcement:write",   "Announcement Write",   "Post delivery announcements.",         "ANNOUNCEMENT", "AGENCY", false, true);
        add(catalog, "announcement:respond", "Announcement Respond", "Respond to delivery announcements.",   "ANNOUNCEMENT", "AGENCY", false, true);
        add(catalog, "announcement:elect",   "Courier Election",     "Elect a courier for an announcement.", "ANNOUNCEMENT", "AGENCY", false, true);

        // ── Accounting permissions ────────────────────────────────────────────────
        add(catalog, "accounting:read",  "Accounting Read",  "Read chart of accounts and journal entries.", "ACCOUNTING", "ORGANIZATION", false, true);
        add(catalog, "accounting:write", "Accounting Write", "Post journal entries.",                       "ACCOUNTING", "ORGANIZATION", false, true);
        add(catalog, "accounting:admin", "Accounting Admin", "Initialize chart of accounts, close periods.", "ACCOUNTING", "TENANT",      false, true);

        // ── Sales permissions ─────────────────────────────────────────────────────
        add(catalog, "sales:read",  "Sales Read",  "View sales orders.",              "SALES", "AGENCY",       false, true);
        add(catalog, "sales:write", "Sales Write", "Create and manage sales orders.", "SALES", "AGENCY",       false, true);
        add(catalog, "sales:admin", "Sales Admin", "Full sales administration.",      "SALES", "ORGANIZATION", false, true);

        // ── Administration permissions ────────────────────────────────────────────
        add(catalog, "administration:read",                    "Admin Read",           "Read administration data.",             "ADMINISTRATION", "AGENCY",       false, true);
        add(catalog, "administration:write",                   "Admin Write",          "Write administration data.",            "ADMINISTRATION", "ORGANIZATION", false, true);
        add(catalog, "administration:roles:read",              "Roles Read",           "Read roles and templates.",             "ADMINISTRATION", "ORGANIZATION", false, true);
        add(catalog, "administration:roles:write",             "Roles Write",          "Create and manage roles.",              "ADMINISTRATION", "ORGANIZATION", false, true);
        add(catalog, "administration:roles:clone",             "Roles Clone",          "Clone role templates.",                 "ADMINISTRATION", "ORGANIZATION", false, true);
        add(catalog, "administration:permissions:read",        "Permissions Read",     "Read permission catalog.",              "ADMINISTRATION", "ORGANIZATION", false, true);
        add(catalog, "administration:assignments:write",       "Assignments Write",    "Assign roles to users.",                "ADMINISTRATION", "AGENCY",       false, true);
        add(catalog, "administration:settings:read",           "Settings Read",        "Read platform settings.",               "ADMINISTRATION", "ORGANIZATION", false, true);
        add(catalog, "administration:settings:write",          "Settings Write",       "Write platform settings.",              "ADMINISTRATION", "TENANT",       false, true);
        add(catalog, "administration:audit:read",              "Audit Read",           "Read audit trails.",                    "ADMINISTRATION", "TENANT",       false, true);
        add(catalog, "administration:govern:business-actors",  "Govern Actors",        "Govern business actor lifecycle.",      "ADMINISTRATION", "TENANT",       false, true);
        add(catalog, "administration:govern:organizations",    "Govern Organizations", "Govern organization lifecycle.",        "ADMINISTRATION", "TENANT",       false, true);
        add(catalog, "administration:govern:agencies",         "Govern Agencies",      "Govern agency lifecycle.",              "ADMINISTRATION", "ORGANIZATION", false, true);
        add(catalog, "tnt:platform:admin", "TiiBnTick Platform Admin", "Full TiiBnTick platform administration.", "PLATFORM", "SYSTEM", true, false);

        // ════════════════════════════════════════════════════════════════════════
        // SECTION 2: TntPermission.* entries (tnt-roles-core resource:action format)
        // These are the canonical permissions used by @RequirePermission AOP and
        // TntPermissionEvaluator fast-path evaluation. Added in .
        // ════════════════════════════════════════════════════════════════════════

        // ── Mission management (tnt-delivery-core) ────────────────────────────────
        add(catalog, "mission:create",   "Mission Create",   "Create a new delivery mission.",           "MISSION",   "AGENCY",        false, true);
        add(catalog, "mission:assign",   "Mission Assign",   "Assign a mission to a deliverer.",         "MISSION",   "AGENCY",        false, true);
        add(catalog, "mission:start",    "Mission Start",    "Start a delivery mission.",                "MISSION",   "AGENCY",        false, true);
        add(catalog, "mission:complete", "Mission Complete", "Mark a delivery mission as completed.",    "MISSION",   "AGENCY",        false, true);

        // ── Delivery operations ────────────────────────────────────────────────────
        add(catalog, "delivery:confirm", "Delivery Confirm", "Confirm delivery completion.",              "DELIVERY", "AGENCY",        false, true);
        add(catalog, "delivery:proof",   "Delivery Proof",   "Attach delivery proof (photo/signature).", "DELIVERY", "AGENCY",        false, true);

        // ── Actor management (tnt-actor-core) ────────────────────────────────────
        add(catalog, "actor:read",    "Actor Read",    "Read actor profiles and ratings.",                  "ACTOR", "AGENCY",       false, true);
        add(catalog, "actor:write",   "Actor Write",   "Create and update actor profiles.",                 "ACTOR", "ORGANIZATION", false, true);
        add(catalog, "actor:approve", "Actor Approve", "Approve actor KYC and activation.",                 "ACTOR", "ORGANIZATION", false, true);
        add(catalog, "actor:suspend", "Actor Suspend", "Suspend an actor account for policy violations.",   "ACTOR", "ORGANIZATION", false, true);

        // ── Billing and financial permissions (tnt-billing-*) ────────────────────
        add(catalog, "billing:read",  "Billing Read",  "Read billing records and invoices.",                "BILLING", "ORGANIZATION", false, true);
        add(catalog, "billing:write", "Billing Write", "Create and update billing records.",                "BILLING", "ORGANIZATION", false, true);
        add(catalog, "billing:post",  "Billing Post",  "Post financial transactions to ledger.",            "BILLING", "TENANT",       false, true);

        // ── Invoice operations ────────────────────────────────────────────────────
        add(catalog, "invoice:read",  "Invoice Read",  "Read invoices and billing history.",                "INVOICE", "ORGANIZATION", false, true);
        add(catalog, "invoice:issue", "Invoice Issue", "Issue new invoices (TNT-FACT-*).",                  "INVOICE", "ORGANIZATION", false, true);

        // ── Wallet and payment operations ─────────────────────────────────────────
        add(catalog, "wallet:read",      "Wallet Read",      "Read wallet balance and transactions.",      "WALLET",  "AGENCY",        false, true);
        add(catalog, "wallet:write",     "Wallet Write",     "Top up or manage wallet funds.",             "WALLET",  "AGENCY",        false, true);
        add(catalog, "payment:process",  "Payment Process",  "Process payments (MTN MoMo, Orange, etc.).", "PAYMENT", "ORGANIZATION",  false, true);
        add(catalog, "payment:refund",   "Payment Refund",   "Issue refunds to clients.",                  "PAYMENT", "ORGANIZATION",  false, true);

        // ── Report and export operations ─────────────────────────────────────────
        add(catalog, "report:read",   "Report Read",   "Read platform reports and KPIs.",                  "REPORT", "ORGANIZATION", false, true);
        add(catalog, "report:export", "Report Export", "Export reports to CSV/PDF.",                       "REPORT", "ORGANIZATION", false, true);

        // ── Trust Layer / Blockchain (tnt-trust) ─────────────────────────────────
        add(catalog, "trust:read",   "Trust Read",   "Read blockchain proof records.",                     "TRUST", "ORGANIZATION", false, true);
        add(catalog, "trust:verify", "Trust Verify", "Verify delivery proofs on blockchain.",              "TRUST", "ORGANIZATION", false, true);
        add(catalog, "trust:anchor", "Trust Anchor", "Anchor a new proof on the blockchain.",              "TRUST", "ORGANIZATION", false, true);

        // ── Relay point operations (tnt-resource-core) ───────────────────────────
        add(catalog, "relay:read",    "Relay Read",    "Read relay point information.",                    "RELAY", "AGENCY",        false, true);
        add(catalog, "relay:write",   "Relay Write",   "Create and configure relay points.",               "RELAY", "ORGANIZATION",  false, true);
        add(catalog, "relay:operate", "Relay Operate", "Perform relay point operations (receive/handover).","RELAY","AGENCY",        false, true);

        // ── Media / File management (tnt-media-core) ─────────────────────────────
        add(catalog, "media:read",   "Media Read",   "Read media files and QR codes.",                     "MEDIA", "AGENCY",        false, true);
        add(catalog, "media:upload", "Media Upload", "Upload media files and documents.",                  "MEDIA", "AGENCY",        false, true);
        add(catalog, "media:delete", "Media Delete", "Delete media files.",                                "MEDIA", "ORGANIZATION",  false, true);

        // ── Resource management ───────────────────────────────────────────────────
        add(catalog, "resource:read",    "Resource Read",    "Read vehicles, equipment, and staff.",       "RESOURCE", "AGENCY",       false, true);
        add(catalog, "resource:write",   "Resource Write",   "Create and manage fleet resources.",         "RESOURCE", "ORGANIZATION", false, true);
        add(catalog, "resource:reserve", "Resource Reserve", "Reserve a vehicle or equipment.",            "RESOURCE", "AGENCY",       false, true);

        // ── Admin management (tnt-administration-core) ───────────────────────────
        add(catalog, "admin:roles",    "Admin Roles",    "Manage role assignments.",                        "ADMIN", "ORGANIZATION", false, true);
        add(catalog, "admin:users",    "Admin Users",    "Manage user accounts and profiles.",              "ADMIN", "TENANT",       false, true);
        add(catalog, "admin:audit",    "Admin Audit",    "Access full audit trail.",                        "ADMIN", "TENANT",       false, true);
        add(catalog, "admin:settings", "Admin Settings", "Manage platform settings and configuration.",     "ADMIN", "TENANT",       false, true);

        // ── Scope-level permissions (system-protected) ───────────────────────────
        add(catalog, "system:admin", "System Admin", "Full system-level administration.", "SYSTEM", "SYSTEM", true, false);
        add(catalog, "tenant:admin", "Tenant Admin", "Full tenant-level administration.", "SYSTEM", "TENANT", true, false);
        add(catalog, "org:admin",    "Org Admin",    "Full organization-level administration.", "SYSTEM", "ORGANIZATION", true, false);

        return Collections.unmodifiableMap(catalog);
    }

    /**
     * Adds an entry to the catalog with no Kernel counterpart (kernelPermissionId = null).
     * The Kernel UUID will be resolved at runtime via KernelPermissionPort if applicable.
     */
    private static void add(Map<String, TntPermissionEntry> map, String code, String name,
                             String description, String module, String scope,
                             boolean system, boolean assignable) {
        map.put(code.toLowerCase(),
                new TntPermissionEntry(code, name, description, module, scope, system, assignable));
    }
}
