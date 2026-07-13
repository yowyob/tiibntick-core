package com.yowyob.tiibntick.bootstrap.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry documenting all modules assembled inside {@code tnt-bootstrap}.
 *
 * <p> — Changes from v2.1:
 * <ul>
 *   <li>Added {@code tnt-common-core} (L0 Foundation — shared domain types).</li>
 *   <li>Added {@code tnt-auth-core} (L1 Foundation — security bridge, @CurrentUser).</li>
 *   <li>Added {@code tnt-roles-core} (L1 Foundation — RBAC DSL, @RequirePermission).</li>
 *   <li>Added {@code tnt-incident-core} (L3 Logistics — incident engine, 140+ types).</li>
 *   <li>Updated {@link ModuleLayer} to distinguish L0 Foundation from L1 Foundation.</li>
 *   <li>Module count: 32 (v2.1) → 36 ().</li>
 * </ul>
 *
 * <p>Consumed by:
 * <ul>
 *   <li>{@link com.yowyob.tiibntick.bootstrap.actuator.TntInfoContributor} — /actuator/info</li>
 *   <li>{@link com.yowyob.tiibntick.bootstrap.actuator.TntModuleInventoryEndpoint} — /actuator/tnt/modules</li>
 *   <li>{@link com.yowyob.tiibntick.bootstrap.startup.TntStartupRunner} — startup step 1</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@Getter
public class TntModuleRegistry {

    // ── Inner types ───────────────────────────────────────────────────────────

    public enum ModuleLayer {
        KERNEL_YOWYOB,
        /** L0 — Core event bus, i18n, shared types. */
        FOUNDATION_L0,
        /** L1 — Security bridge, RBAC DSL. New in . */
        FOUNDATION_L1,
        IDENTITY_L2,
        LOGISTICS_L3,
        BUSINESS_L4,
        BILLING_ENGINE_L5,
        /** L6 — Core Backend: official per-product business backends (tnt-link-back-core, ...). */
        CORE_BACKEND_L6
    }

    public enum ModuleOrigin {
        /** Module is provided entirely by the Yowyob Kernel (RT-comops). */
        KERNEL_PROVIDED,
        /** TiiBnTick contributes code back to the Kernel (e.g., yow-event-kernel). */
        KERNEL_CONTRIBUTION,
        /** Module is 100% TiiBnTick-specific — no kernel counterpart. */
        TNT_EXCLUSIVE,
        /** Module extends (specializes) a Kernel module. */
        TNT_EXTENSION
    }

    public enum ModuleStatus {
        ACTIVE, DEGRADED, UNAVAILABLE, STARTING
    }

    public record ModuleDescriptor(
            String moduleId,
            String displayName,
            ModuleLayer layer,
            ModuleOrigin origin,
            ModuleStatus status,
            List<String> dependencies,
            List<String> kafkaTopics,
            String databaseSchema
    ) {}

    public record ModuleReport(
            int totalModules,
            int kernelModules,
            int tntExclusiveModules,
            int tntExtensionModules,
            int kernelContributions,
            int modulesWithSchemas,
            int totalKafkaTopics,
            LocalDateTime generatedAt
    ) {
        public String summary() {
            return String.format(
                    "TiiBnTick Core  — %d modules (%d exclusive, %d extensions, " +
                    "%d kernel contributions) | %d Kafka topics",
                    totalModules, tntExclusiveModules, tntExtensionModules,
                    kernelContributions, totalKafkaTopics);
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final Map<String, ModuleDescriptor> modules = new LinkedHashMap<>();

    // ── Registration ──────────────────────────────────────────────────────────

    @PostConstruct
    public void registerModules() {

        // ── L0 — Foundation ────────────────────────────────────────────────────
        register("yow-event-kernel",
                "Event Bus (Kafka + Outbox Pattern + DLQ + Avro)",
                ModuleLayer.FOUNDATION_L0, ModuleOrigin.KERNEL_CONTRIBUTION,
                List.of(), List.of("tnt.outbox.events", "tnt.dlq"), null);

        register("yow-i18n-kernel",
                "Internationalisation (FR/EN/Pidgin, XAF/NGN/KES/GHS)",
                ModuleLayer.FOUNDATION_L0, ModuleOrigin.KERNEL_CONTRIBUTION,
                List.of(), List.of(), null);

        register("tnt-common-core",
                "Common Core — Shared TiiBnTick domain types, value objects, base exceptions",
                ModuleLayer.FOUNDATION_L0, ModuleOrigin.TNT_EXCLUSIVE,
                List.of(), List.of(), null);

        // ── L1 — Foundation (Security + RBAC) ─────────────────────────────────
        register("tnt-auth-core",
                "Auth Core — Security bridge: Kernel JWT → TntSecurityContext + @CurrentUser",
                ModuleLayer.FOUNDATION_L1, ModuleOrigin.TNT_EXTENSION,
                List.of("RT-comops-kernel-core", "RT-comops-auth-core", "tnt-common-core"),
                List.of(), null);

        register("tnt-roles-core",
                "Roles Core — TiiBnTick RBAC: TntRole enum, TntPermission constants, " +
                "@RequirePermission AOP, TntPermissionEvaluator",
                ModuleLayer.FOUNDATION_L1, ModuleOrigin.TNT_EXTENSION,
                List.of("RT-comops-roles-core", "RT-comops-kernel-core", "tnt-common-core"),
                List.of(), null);

        // ── L2 — Identity ──────────────────────────────────────────────────────
        register("tnt-actor-core",
                "Actor Core (DelivererProfile, FreelancerProfile, GPS, Reputation, " +
                "IYowAuthTntAdapter impl, IActorReputationPort impl)",
                ModuleLayer.IDENTITY_L2, ModuleOrigin.TNT_EXTENSION,
                List.of("comops-actor-core", "tnt-auth-core", "tnt-roles-core"),
                List.of("tnt.actor.profile.updated", "tnt.actor.reputation.changed"),
                "tnt_actor");

        register("tnt-organization-core",
                "Organization Core (Agency, Branch, ServiceZone, RelayHub, African POI)",
                ModuleLayer.IDENTITY_L2, ModuleOrigin.TNT_EXTENSION,
                List.of("comops-organization-core", "tnt-auth-core"),
                List.of(), "tnt_organization");

        register("tnt-tp-core",
                "Third-Party Core (Shippers, Fidelity, mobile KYC, GDPR masking, Rating)",
                ModuleLayer.IDENTITY_L2, ModuleOrigin.TNT_EXTENSION,
                List.of("comops-tp-core", "tnt-auth-core"),
                List.of(), "tnt_tp");

        register("tnt-administration-core",
                "Administration Core (RBAC role management, Freelancer KYC, " +
                "Dispute governance, TntRoleDefinitionRegistry provisioning)",
                ModuleLayer.IDENTITY_L2, ModuleOrigin.TNT_EXTENSION,
                List.of("comops-administration-core", "tnt-auth-core", "tnt-roles-core"),
                List.of(), "tnt_administration");

        // ── L3 — Logistics ─────────────────────────────────────────────────────
        register("tnt-geo-core",
                "Geo Core (PostGIS road graph, OSM geocoding, Weather, African POI)",
                ModuleLayer.LOGISTICS_L3, ModuleOrigin.TNT_EXCLUSIVE,
                List.of(),
                List.of("tnt.geo.gps.position-updated"), "tnt_geo");

        register("tnt-route-core",
                "Route Core (A* graph search, VRP OR-Tools 9.8.3296, Kalman ETA, ω(a,t), " +
                "IRouteOptimizerPort impl for tnt-incident-core)",
                ModuleLayer.LOGISTICS_L3, ModuleOrigin.TNT_EXCLUSIVE,
                List.of("tnt-geo-core"),
                List.of("tnt.realtime.eta.updated"), null);

        register("tnt-delivery-core",
                "Delivery Core (Mission AR, Package, HubDeposit, DeliveryProof, SLA, " +
                "IMissionStatusPort impl for tnt-incident-core, pausedByIncidentId field)",
                ModuleLayer.LOGISTICS_L3, ModuleOrigin.TNT_EXCLUSIVE,
                List.of("tnt-geo-core", "tnt-actor-core", "tnt-organization-core",
                        "tnt-auth-core", "tnt-roles-core"),
                List.of("tnt.delivery.mission.created", "tnt.delivery.mission.status-changed",
                        "tnt.delivery.package.picked-up", "tnt.delivery.package.delivered",
                        "tnt.delivery.hub-deposit.created", "tnt.delivery.hub-deposit.picked-up"),
                "tnt_delivery");

        register("tnt-incident-core",
                "Incident Core — 140+ incident types, auto-resolution, inter-agency cooperation, " +
                "blockchain evidence chain, SLA monitoring schedulers",
                ModuleLayer.LOGISTICS_L3, ModuleOrigin.TNT_EXCLUSIVE,
                List.of("tnt-delivery-core", "tnt-actor-core", "tnt-resource-core",
                        "tnt-route-core", "tnt-notify-core", "tnt-media-core",
                        "tnt-auth-core", "tnt-roles-core"),
                List.of(
                        "tnt.incident.created", "tnt.incident.status.changed",
                        "tnt.incident.triaged", "tnt.incident.driver.assigned",
                        "tnt.incident.handover.completed", "tnt.incident.resolved",
                        "tnt.incident.closed", "tnt.incident.cancelled",
                        "tnt.incident.escalated", "tnt.incident.escalated.to.dispute",
                        "tnt.incident.interagency.requested", "tnt.incident.interagency.completed"),
                "tnt_incident");

        register("tnt-dispute-core",
                "Dispute Core (Litiges, Evidence, Refund, Compensation — " +
                "consumes tnt.incident.escalated.to.dispute)",
                ModuleLayer.LOGISTICS_L3, ModuleOrigin.TNT_EXCLUSIVE,
                List.of("tnt-delivery-core", "tnt-actor-core", "tnt-auth-core", "tnt-roles-core"),
                List.of("tnt.dispute.opened", "tnt.dispute.resolved", "tnt.dispute.escalated",
                        "tnt.dispute.evidence-added", "tnt.dispute.refund-initiated",
                        "tnt.dispute.compensation-paid", "tnt.dispute.closed"),
                "tnt_dispute");

        register("tnt-realtime-core",
                "Realtime Core (WebSocket STOMP broker, GPS stream ingestion, Presence manager, " +
                "enriched GPS events with trajectoryAnomaly/prolongedStop for tnt-incident-core)",
                ModuleLayer.LOGISTICS_L3, ModuleOrigin.TNT_EXCLUSIVE,
                List.of("tnt-route-core"),
                List.of("tnt.realtime.presence.changed",
                        "tnt.realtime.gps.position.updated",
                        "tnt.realtime.geofence.triggered"), null);

        register("tnt-sync-core",
                "Sync Core (Offline-First PWA, DuckDB-Wasm bridge, Delta sync, Conflict LWW)",
                ModuleLayer.LOGISTICS_L3, ModuleOrigin.TNT_EXCLUSIVE,
                List.of(),
                List.of("tnt.sync.delta.requested", "tnt.sync.conflict.detected"), null);

        register("tnt-notify-core",
                "Notify Core (FCM, MTN SMS, Orange SMS, WhatsApp Meta, Email, in-app STOMP, " +
                "INotificationPort impl for tnt-incident-core — 10+ incident notification types)",
                ModuleLayer.LOGISTICS_L3, ModuleOrigin.TNT_EXTENSION,
                List.of("yow-i18n-kernel", "tnt-auth-core"),
                List.of("tnt.notify.notification.requested", "tnt.notify.notification.delivered",
                        "tnt.notify.notification.failed"),
                "tnt_notify");

        register("tnt-media-core",
                "Media Core (ZXing QR+HMAC, JasperReports PDF, MinIO bucket-per-tenant, " +
                "IMediaStoragePort impl for tnt-incident-core — tnt-incident-evidences bucket)",
                ModuleLayer.LOGISTICS_L3, ModuleOrigin.TNT_EXTENSION,
                List.of("comops-file-core"),
                List.of("tnt.media.file.uploaded", "tnt.media.file.deleted"),
                "tnt_media");

        // ── L4 — Business ──────────────────────────────────────────────────────
        register("tnt-resource-core",
                "Resource Core (Vehicle, Equipment, Deliverer assignment, Maintenance, " +
                "IDriverAvailabilityPort + IVehicleCompatibilityPort impl for tnt-incident-core, " +
                "IN_INCIDENT_SUBSTITUTION vehicle status)",
                ModuleLayer.BUSINESS_L4, ModuleOrigin.TNT_EXTENSION,
                List.of("comops-resource-core", "tnt-auth-core", "tnt-roles-core"),
                List.of(), "tnt_resource");

        register("tnt-product-core",
                "Product Core (ServiceOffer logistics, LogisticsProfile, Market publication)",
                ModuleLayer.BUSINESS_L4, ModuleOrigin.TNT_EXTENSION,
                List.of("comops-product-core"),
                List.of(), "tnt_product");

        register("tnt-inventory-core",
                "Inventory Core (StockEntry per Hub, in/out tracking, alerts)",
                ModuleLayer.BUSINESS_L4, ModuleOrigin.TNT_EXTENSION,
                List.of("comops-inventory-core"),
                List.of(), "tnt_inventory");

        register("tnt-sales-core",
                "Sales Core (SalesOrder, dispatch, link invoice/mission)",
                ModuleLayer.BUSINESS_L4, ModuleOrigin.TNT_EXTENSION,
                List.of("comops-sales-core"),
                List.of(), "tnt_sales");

        register("tnt-accounting-core",
                "Accounting Core (OHADA journal, balance, multi-country reconciliation)",
                ModuleLayer.BUSINESS_L4, ModuleOrigin.TNT_EXTENSION,
                List.of("comops-accounting-core"),
                List.of(), "tnt_accounting");

        // ── L5 — Billing Engine ────────────────────────────────────────────────
        register("tnt-billing-dsl",
                "Billing DSL (AST-based pricing expressions, formula parser)",
                ModuleLayer.BILLING_ENGINE_L5, ModuleOrigin.TNT_EXCLUSIVE,
                List.of(), List.of(), null);

        register("tnt-billing-pricing",
                "Billing Pricing (Selling price computation: distance, weight, zone, urgency)",
                ModuleLayer.BILLING_ENGINE_L5, ModuleOrigin.TNT_EXCLUSIVE,
                List.of("tnt-billing-dsl", "tnt-geo-core"),
                List.of(), null);

        register("tnt-billing-cost",
                "Billing Cost (Operational cost: fuel consumption, vehicle wear, driver time)",
                ModuleLayer.BILLING_ENGINE_L5, ModuleOrigin.TNT_EXCLUSIVE,
                List.of("tnt-billing-dsl"),
                List.of(), null);

        register("tnt-billing-invoice",
                "Billing Invoice (Invoice lifecycle, TNT-FACT-{tenant}-{year}-{seq}, TVA CM/NG/KE, " +
                "@RequirePermission guards on invoice:issue)",
                ModuleLayer.BILLING_ENGINE_L5, ModuleOrigin.TNT_EXCLUSIVE,
                List.of("tnt-billing-pricing", "tnt-media-core", "tnt-roles-core"),
                List.of("tnt.billing.invoice.created"), "tnt_billing");

        register("tnt-billing-wallet",
                "Billing Wallet (MTN MoMo, Orange Money, Stripe, in-app wallet, " +
                "IPaymentFreezePort impl for tnt-incident-core, " +
                "@RequirePermission guards on payment:process/refund)",
                ModuleLayer.BILLING_ENGINE_L5, ModuleOrigin.TNT_EXCLUSIVE,
                List.of("tnt-billing-invoice", "tnt-roles-core"),
                List.of("tnt.billing.payment.confirmed", "tnt.billing.payment.failed",
                        "tnt.billing.wallet.credited", "tnt.billing.wallet.debited"),
                "tnt_billing");

        register("tnt-billing-report",
                "Billing Report (Revenue, Commissions, Margins, KPIs, CSV/PDF export, " +
                "@RequirePermission guard on report:export)",
                ModuleLayer.BILLING_ENGINE_L5, ModuleOrigin.TNT_EXCLUSIVE,
                List.of("tnt-billing-invoice", "tnt-billing-wallet", "tnt-roles-core"),
                List.of(), null);
    
        // ── : tnt-billing-templates (new module) ──────────────────────────────
        register("tnt-billing-templates",
                "Billing Policy Template Catalog — predefined DSL templates for FreelancerOrg, " +
                "Agency, and Point actors. Enables one-click billing policy creation. " +
                "Supports SIMPLIFIED (FreelancerOrg/Point, max 20 rules) and FULL (Agency/Admin, max 100 rules) DSL levels.",
                ModuleLayer.BILLING_ENGINE_L5, ModuleOrigin.TNT_EXCLUSIVE,
                List.of("tnt-billing-core", "tnt-administration-core"),
                List.of("tnt.billing.template.applied", "tnt.billing.custom_template.saved"),
                "billing");

        // ── L6 — Core Backend (per-product official business backends) ────────
        register("tnt-link-back-core",
                "Link Back Core — Official business backend for TiiBnTick Link: network nodes, " +
                "bulletin board, DAO zones, gamification/leaderboard, orchestrates tnt-geo-core, " +
                "tnt-route-core, tnt-delivery-core, tnt-incident-core, tnt-realtime-core, " +
                "tnt-sync-core, tnt-notify-core, tnt-actor-core, tnt-organization-core. Consumed " +
                "exclusively by the Link BFF (tiibntick-link-backend) over HTTP.",
                ModuleLayer.CORE_BACKEND_L6, ModuleOrigin.TNT_EXCLUSIVE,
                List.of("tnt-auth-core", "tnt-roles-core", "tnt-actor-core", "tnt-organization-core",
                        "tnt-geo-core", "tnt-route-core", "tnt-delivery-core", "tnt-incident-core",
                        "tnt-realtime-core", "tnt-sync-core", "tnt-notify-core"),
                List.of(), "tnt_link");

        log.info("TiiBnTick Module Registry  — {} modules registered", modules.size());
        log.info(generateReport().summary());
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Map<String, ModuleDescriptor> getAll() {
        return Collections.unmodifiableMap(modules);
    }

    public int count() {
        return modules.size();
    }

    public List<ModuleDescriptor> getByLayer(ModuleLayer layer) {
        return modules.values().stream()
                .filter(m -> m.layer() == layer)
                .toList();
    }

    public List<ModuleDescriptor> getByOrigin(ModuleOrigin origin) {
        return modules.values().stream()
                .filter(m -> m.origin() == origin)
                .toList();
    }

    public ModuleReport generateReport() {
        int totalTopics = modules.values().stream()
                .mapToInt(m -> m.kafkaTopics().size())
                .sum();
        return new ModuleReport(
                modules.size(),
                (int) modules.values().stream().filter(m -> m.origin() == ModuleOrigin.KERNEL_PROVIDED).count(),
                (int) modules.values().stream().filter(m -> m.origin() == ModuleOrigin.TNT_EXCLUSIVE).count(),
                (int) modules.values().stream().filter(m -> m.origin() == ModuleOrigin.TNT_EXTENSION).count(),
                (int) modules.values().stream().filter(m -> m.origin() == ModuleOrigin.KERNEL_CONTRIBUTION).count(),
                (int) modules.values().stream().filter(m -> m.databaseSchema() != null).count(),
                totalTopics,
                LocalDateTime.now()
        );
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void register(String id, String name, ModuleLayer layer, ModuleOrigin origin,
                          List<String> deps, List<String> topics, String schema) {
        modules.put(id, new ModuleDescriptor(
                id, name, layer, origin, ModuleStatus.ACTIVE, deps, topics, schema));
    }
}
