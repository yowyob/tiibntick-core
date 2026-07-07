package com.yowyob.tiibntick.core.administration.domain.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Provides the TiiBnTick-specific role templates that extend the Kernel's defaults
 * (RT-comops-roles-core). These templates map to the concrete actor types in TiiBnTick
 * platforms (Agency, Freelancer, PointRelais, etc.).
 *
 * <p> — Augmented with the 9 canonical TiiBnTick role templates that align with
 * {@code TntRole} enum in {@code tnt-roles-core}. These canonical templates are used by
 * {@code TntRoleInitializationService} (tnt-roles-core) for system-level provisioning.
 * The administration module provisions both the full operational template list AND the
 * canonical role templates when onboarding a new tenant.
 *
 * <p>Each {@link TntRoleTemplate} carries an optional {@code kernelRoleId} which is the UUID
 * of the corresponding role in the Kernel. This UUID is null in the static template list
 * and is populated per-tenant at provisioning time, stored in {@code TntRoleDefinition}.
 *
 * @author MANFOUO Braun
 */
@Component
public class TntRoleTemplateRegistry {

    /**
     * Immutable descriptor for a TiiBnTick role template.
     *
     * @param code              Unique TNT template code (e.g., "TNT_DISPATCHER")
     * @param name              Human-readable name
     * @param scopeType         Applicable scope: TENANT, ORGANIZATION, AGENCY
     * @param permissions       Set of permission codes this template grants
     * @param protectedTemplate If true, this role cannot be deleted after provisioning
     * @param kernelRoleId      UUID of the Kernel role if this template has been provisioned
     *                          and linked to the Kernel. Null in the static catalog.
     */
    public record TntRoleTemplate(
            String code,
            String name,
            String scopeType,
            Set<String> permissions,
            boolean protectedTemplate,
            UUID kernelRoleId
    ) {
        /** Convenience constructor — kernelRoleId defaults to null for static catalog entries. */
        public TntRoleTemplate(String code, String name, String scopeType,
                                Set<String> permissions, boolean protectedTemplate) {
            this(code, name, scopeType, permissions, protectedTemplate, null);
        }

        /** Returns a copy of this template with the given Kernel role UUID linked. */
        public TntRoleTemplate withKernelRoleId(UUID roleId) {
            return new TntRoleTemplate(code, name, scopeType, permissions, protectedTemplate, roleId);
        }
    }

    /**
     * Returns the complete list of TiiBnTick role templates — both operational templates
     * for agency/org management AND the 9 canonical roles from {@code TntRole} enum.
     *
     * <p>The {@code kernelRoleId} field is null for all entries in this static list.
     * Per-tenant Kernel role UUIDs are tracked in {@code TntRoleDefinition}.
     */
    public List<TntRoleTemplate> getTemplates() {
        List<TntRoleTemplate> templates = new ArrayList<>();

        // ════════════════════════════════════════════════════════════════════════
        // SECTION A: Canonical TntRole templates (aligned with tnt-roles-core TntRole enum)
        // These 9 roles are provisioned by TntRoleInitializationService at system startup
        // and by provisionForTenant() for each new tenant onboarding.
        // ════════════════════════════════════════════════════════════════════════

        templates.add(new TntRoleTemplate("AGENCY_MANAGER", "Agency Manager", "AGENCY",
                Set.of("agency:read", "agency:write", "agency:manage",
                        "branch:read", "branch:write", "branch:manage",
                        "mission:create", "mission:assign", "mission:start", "mission:complete",
                        "actor:read", "actor:write", "actor:approve", "actor:suspend",
                        "billing:read", "billing:write",
                        "invoice:read", "invoice:issue",
                        "report:read", "report:export",
                        "dispute:create", "dispute:read", "dispute:resolve",
                        "delivery:read", "delivery:track", "delivery:confirm",
                        "administration:assignments:write"),
                false));

        templates.add(new TntRoleTemplate("BRANCH_MANAGER", "Branch Manager", "AGENCY",
                Set.of("branch:read", "branch:write",
                        "mission:create", "mission:assign",
                        "delivery:read", "delivery:track",
                        "actor:read",
                        "relay:read", "relay:write", "relay:operate",
                        "report:read"),
                false));

        templates.add(new TntRoleTemplate("PERMANENT_DELIVERER", "Permanent Deliverer", "AGENCY",
                Set.of("mission:create", "mission:start", "mission:complete",
                        "delivery:read", "delivery:track", "delivery:confirm", "delivery:proof",
                        "wallet:read",
                        "media:read", "media:upload"),
                false));

        templates.add(new TntRoleTemplate("FREELANCER", "Freelancer Deliverer", "TENANT",
                Set.of("announcement:read", "announcement:respond",
                        "mission:start", "mission:complete",
                        "delivery:read", "delivery:confirm", "delivery:proof",
                        "wallet:read",
                        "media:read", "media:upload"),
                false));

        templates.add(new TntRoleTemplate("RELAY_OPERATOR", "Relay Point Operator", "AGENCY",
                Set.of("relay:read", "relay:write", "relay:operate",
                        "delivery:read", "delivery:track",
                        "media:read", "media:upload",
                        "report:read"),
                false));

        templates.add(new TntRoleTemplate("CLIENT", "Client", "TENANT",
                Set.of("announcement:create",
                        "delivery:read", "delivery:track",
                        "wallet:read", "wallet:write", "payment:process",
                        "invoice:read",
                        "dispute:create", "dispute:read",
                        "media:read"),
                false));

        templates.add(new TntRoleTemplate("SUPPORT_AGENT", "Support Agent", "TENANT",
                Set.of("delivery:read", "delivery:track",
                        "actor:read",
                        "dispute:read", "dispute:resolve",
                        "billing:read",
                        "report:read",
                        "trust:read", "trust:verify"),
                false));

        templates.add(new TntRoleTemplate("ORG_ADMIN", "Organization Admin", "ORGANIZATION",
                Set.of("org:admin",
                        "agency:read", "agency:write", "agency:manage",
                        "actor:read", "actor:write",
                        "billing:read", "billing:write",
                        "report:read", "report:export",
                        "admin:roles", "admin:users", "admin:audit"),
                false));

        templates.add(new TntRoleTemplate("TNT_ADMIN", "TiiBnTick Super Admin", "SYSTEM",
                Set.of("*"),
                true));

        // ════════════════════════════════════════════════════════════════════════
        // SECTION B: Operational TNT templates (legacy administration catalog)
        // These templates map to operational roles in agencies and organizations.
        // ════════════════════════════════════════════════════════════════════════

        templates.add(new TntRoleTemplate("TNT_SUPER_ADMIN", "TiiBnTick Super Admin", "TENANT",
                Set.of("administration:read", "administration:write",
                        "administration:roles:read", "administration:roles:write",
                        "administration:roles:clone", "administration:permissions:read",
                        "administration:assignments:write",
                        "administration:settings:read", "administration:settings:write",
                        "administration:audit:read",
                        "administration:govern:business-actors",
                        "administration:govern:organizations",
                        "administration:govern:agencies",
                        "delivery:admin", "accounting:admin", "sales:admin",
                        "freelancer:approve", "freelancer:write", "point-relais:write",
                        "dispute:admin", "announcement:write", "tnt:platform:admin"),
                true));

        templates.add(new TntRoleTemplate("TNT_PLATFORM_ADMIN", "TiiBnTick Platform Admin", "TENANT",
                Set.of("administration:read", "administration:write",
                        "administration:roles:read", "administration:roles:write",
                        "administration:roles:clone",
                        "administration:settings:read", "administration:settings:write",
                        "administration:audit:read",
                        "administration:govern:business-actors",
                        "administration:govern:organizations",
                        "delivery:admin", "accounting:admin", "sales:admin",
                        "dispute:admin", "freelancer:approve", "announcement:write"),
                true));

        templates.add(new TntRoleTemplate("TNT_ORG_MANAGER", "Organization Manager", "ORGANIZATION",
                Set.of("administration:read", "administration:roles:read",
                        "administration:govern:agencies",
                        "delivery:admin", "accounting:read", "accounting:write",
                        "sales:admin", "freelancer:write", "freelancer:approve",
                        "point-relais:write", "dispute:resolve", "announcement:write"),
                false));

        templates.add(new TntRoleTemplate("TNT_ORG_ACCOUNTANT", "Organization Accountant", "ORGANIZATION",
                Set.of("accounting:read", "accounting:write", "accounting:admin", "sales:read"),
                false));

        templates.add(new TntRoleTemplate("TNT_AGENCY_ADMIN", "Agency Admin", "AGENCY",
                Set.of("administration:read", "administration:assignments:write",
                        "delivery:read", "delivery:write", "delivery:dispatch", "delivery:track",
                        "sales:read", "sales:write", "accounting:read",
                        "freelancer:read", "point-relais:read",
                        "dispute:read", "dispute:create",
                        "announcement:read", "announcement:write", "announcement:elect"),
                false));

        templates.add(new TntRoleTemplate("TNT_DISPATCHER", "Dispatcher", "AGENCY",
                Set.of("delivery:read", "delivery:write", "delivery:dispatch", "delivery:track",
                        "sales:read", "sales:write",
                        "announcement:read", "announcement:elect"),
                false));

        templates.add(new TntRoleTemplate("TNT_CUSTOMER_SERVICE", "Customer Service Agent", "AGENCY",
                Set.of("delivery:read", "delivery:track", "sales:read",
                        "dispute:read", "dispute:create", "announcement:read"),
                false));

        templates.add(new TntRoleTemplate("TNT_CASHIER", "Cashier", "AGENCY",
                Set.of("sales:read", "sales:write", "accounting:read"),
                false));

        templates.add(new TntRoleTemplate("TNT_FREELANCER", "Freelancer Courier", "AGENCY",
                Set.of("delivery:track", "freelancer:mission:take",
                        "announcement:read", "announcement:respond"),
                false));

        templates.add(new TntRoleTemplate("TNT_FREELANCER_SENIOR", "Senior Freelancer Courier", "AGENCY",
                Set.of("delivery:read", "delivery:track", "freelancer:mission:take",
                        "announcement:read", "announcement:respond", "dispute:read"),
                false));

        templates.add(new TntRoleTemplate("TNT_POINT_RELAIS_OPERATOR", "Point Relais Operator", "AGENCY",
                Set.of("point-relais:read", "point-relais:receive", "point-relais:handover",
                        "delivery:track"),
                false));

        templates.add(new TntRoleTemplate("TNT_POINT_RELAIS_MANAGER", "Point Relais Manager", "AGENCY",
                Set.of("point-relais:read", "point-relais:write",
                        "point-relais:receive", "point-relais:handover",
                        "delivery:read", "delivery:track"),
                false));

        templates.add(new TntRoleTemplate("TNT_BLOCKCHAIN_AUDITOR", "Blockchain Auditor", "ORGANIZATION",
                Set.of("blockchain:wallet:read", "blockchain:transaction:read", "blockchain:block:read",
                        "trust:read", "trust:verify"),
                false));

        templates.add(new TntRoleTemplate("TNT_TRUST_OPERATOR", "Trust Operator", "ORGANIZATION",
                Set.of("blockchain:wallet:read", "blockchain:wallet:create",
                        "blockchain:transaction:read", "blockchain:transaction:sign",
                        "blockchain:anchor:create", "blockchain:block:read",
                        "trust:read", "trust:verify", "trust:anchor"),
                false));

        templates.add(new TntRoleTemplate("TNT_CLIENT", "Client", "AGENCY",
                Set.of("delivery:track", "announcement:write", "announcement:read",
                        "dispute:create"),
                false));

        // ════════════════════════════════════════════════════════════════════════
        // SECTION D: FreelancerOrganization roles ()
        // Added for the FreelancerOrg model: OWNER managing the org, SUB_DELIVERER executing missions.
        // ════════════════════════════════════════════════════════════════════════

        templates.add(new TntRoleTemplate("FREELANCER_ORG_OWNER", "FreelancerOrg Owner", "TENANT",
                Set.of(
                        // Fleet and org management
                        "freelancer_org:manage_fleet",
                        "freelancer_org:manage_sub_deliverers",
                        "freelancer_org:view_stats",
                        "freelancer_org:invite_sub_deliverer",
                        "freelancer_org:remove_sub_deliverer",
                        // Billing policy
                        "billing_policy:define_own",
                        "billing_templates:use",
                        "billing_invoice:emit_own",
                        "billing_invoice:read",
                        // Delivery and announcements
                        "delivery:read", "delivery:track", "delivery:confirm", "delivery:proof",
                        "announcement:read", "announcement:respond", "announcement:elect",
                        "mission:start", "mission:complete",
                        // Wallet and finance
                        "wallet:read", "wallet:write",
                        // Dispute
                        "dispute:create", "dispute:read",
                        // Profile and media
                        "media:read", "media:upload", "profile:manage_own"),
                false));

        templates.add(new TntRoleTemplate("FREELANCER_SUB_DELIVERER", "FreelancerOrg Sub-Deliverer", "TENANT",
                Set.of(
                        // Mission execution
                        "mission:view_assigned",
                        "mission:execute",
                        "mission:start", "mission:complete",
                        "delivery:track", "delivery:confirm", "delivery:proof",
                        // Profile and earnings
                        "profile:view_own",
                        "wallet:view_own_earnings",
                        "wallet:read",
                        // Media
                        "media:read", "media:upload"),
                false));

        return List.copyOf(templates);
    }

    /**
     * Returns only the 9 canonical TntRole templates (aligned with {@code TntRole} enum
     * in {@code tnt-roles-core}). Used by provisioning logic that specifically targets
     * the canonical role set rather than the full operational template catalog.
     *
     * @return immutable list of the 9 canonical role templates
     */
    public List<TntRoleTemplate> getCanonicalTemplates() {
        return getTemplates().stream()
                .filter(t -> Set.of("AGENCY_MANAGER", "BRANCH_MANAGER", "PERMANENT_DELIVERER",
                        "FREELANCER", "RELAY_OPERATOR", "CLIENT",
                        "SUPPORT_AGENT", "ORG_ADMIN", "TNT_ADMIN")
                        .contains(t.code()))
                .toList();
    }
}
