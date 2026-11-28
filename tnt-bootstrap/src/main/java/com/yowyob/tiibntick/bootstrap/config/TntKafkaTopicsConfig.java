package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.common.kafka.TntTopics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Centralized Kafka topic declarations for all TiiBnTick Core modules.
 *
 * <p>Topics are automatically created by Spring Kafka's {@code KafkaAdmin}
 * if they do not exist on the broker.
 *
 * <p>Naming convention: {@code tnt.{module}.{event}} (lower-case, dot-separated).
 * Replication factor defaults to {@code 1} for dev/local; override via config for staging/prod.
 *
 * <p>All topic name literals live in {@link TntTopics} (tnt-common-core) — the single frozen
 * referential introduced by Audit n°5 · P-03/P-12. This class only decides partitioning/
 * replication per topic; it must never re-declare a topic-name string of its own.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Configuration
public class TntKafkaTopicsConfig {

    @Value("${tnt.kafka.replication-factor:1}")
    private int replicationFactor;

    @Value("${tnt.kafka.partitions:3}")
    private int partitions;

    /** Retention of the aggregate topics: 30 days, so that external platforms can catch up (spec §3.1). */
    @Value("${tnt.kafka.aggregate-retention-ms:2592000000}")
    private long aggregateRetentionMs;

    // ── Aggregate topics (decision [G.1] of 2026-11-26, lot L0) ──────────────────────────────
    // One topic per aggregate, key = aggregate id, body = TntAggregateEvent (cible-evenements-colis-
    // livraison.md §3.1/§3.3); read directly by the external platforms (one group each, read-only ACL).
    @Bean public NewTopic tntParcelEvents()       { return aggregateTopic(TntTopics.PARCEL_EVENTS); }
    @Bean public NewTopic tntDeliveryEvents()     { return aggregateTopic(TntTopics.DELIVERY_EVENTS); }
    @Bean public NewTopic tntIncidentEvents()     { return aggregateTopic(TntTopics.INCIDENT_EVENTS); }
    @Bean public NewTopic tntDisputeEvents()      { return aggregateTopic(TntTopics.DISPUTE_EVENTS); }
    @Bean public NewTopic tntAnnouncementEvents() { return aggregateTopic(TntTopics.ANNOUNCEMENT_EVENTS); }

    // ── Topics produced/consumed by modules but previously never declared (audit kafka-async-wiring) ──
    @Bean public NewTopic tntGofpAnnouncementCandidatesRanked() { return topic(TntTopics.GOFP_ANNOUNCEMENT_CANDIDATES_RANKED); }
    @Bean public NewTopic tntGofpFreelancerCreated() { return topic(TntTopics.GOFP_FREELANCER_CREATED); }
    @Bean public NewTopic tntGofpMatchingNotifications() { return topic(TntTopics.GOFP_MATCHING_NOTIFICATIONS); }
    @Bean public NewTopic tntGofpSubscriptionAttempts() { return topic(TntTopics.GOFP_SUBSCRIPTION_ATTEMPTS); }
    @Bean public NewTopic tntAccountingJournalEntryPosted() { return topic(TntTopics.ACCOUNTING_JOURNAL_ENTRY_POSTED); }
    @Bean public NewTopic tntAccountingPeriodClosed() { return topic(TntTopics.ACCOUNTING_PERIOD_CLOSED); }
    @Bean public NewTopic tntActorBadgeEarned() { return topic(TntTopics.ACTOR_BADGE_EARNED); }
    @Bean public NewTopic tntActorKycValidated() { return topic(TntTopics.ACTOR_KYC_VALIDATED); }
    @Bean public NewTopic tntActorLocationUpdated() { return topic(TntTopics.ACTOR_LOCATION_UPDATED); }
    @Bean public NewTopic tntActorMissionAssigned() { return topic(TntTopics.ACTOR_MISSION_ASSIGNED); }
    @Bean public NewTopic tntActorStatusChanged() { return topic(TntTopics.ACTOR_STATUS_CHANGED); }
    @Bean public NewTopic tntAdministrationEvents() { return topic(TntTopics.ADMINISTRATION_EVENTS); }
    @Bean public NewTopic tntAgencyContractEvents() { return topic(TntTopics.AGENCY_CONTRACT_EVENTS); }
    @Bean public NewTopic tntAgencyEvents() { return topic(TntTopics.AGENCY_EVENTS); }
    @Bean public NewTopic tntAgencyMissionRequest() { return topic(TntTopics.AGENCY_MISSION_REQUEST); }
    @Bean public NewTopic tntAgencyStaffEvents() { return topic(TntTopics.AGENCY_STAFF_EVENTS); }
    @Bean public NewTopic tntBillingWalletCommissionCalculated() { return topic(TntTopics.BILLING_WALLET_COMMISSION_CALCULATED); }
    @Bean public NewTopic tntFreelancerOrgCreated() { return topic(TntTopics.FREELANCER_ORG_CREATED); }
    @Bean public NewTopic tntFreelancerOrgSubDelivererAssociated() { return topic(TntTopics.FREELANCER_ORG_SUB_DELIVERER_ASSOCIATED); }
    @Bean public NewTopic tntFreelancerOrgSubDelivererRevoked() { return topic(TntTopics.FREELANCER_ORG_SUB_DELIVERER_REVOKED); }
    @Bean public NewTopic tntFreelancerOrgVerified() { return topic(TntTopics.FREELANCER_ORG_VERIFIED); }
    @Bean public NewTopic tntGeoNodeEvents() { return topic(TntTopics.GEO_NODE_EVENTS); }
    @Bean public NewTopic tntGeoTrafficEvents() { return topic(TntTopics.GEO_TRAFFIC_EVENTS); }
    @Bean public NewTopic tntGeoZoneEvents() { return topic(TntTopics.GEO_ZONE_EVENTS); }
    @Bean public NewTopic tntInventoryStockLow() { return topic(TntTopics.INVENTORY_STOCK_LOW); }
    @Bean public NewTopic tntMarketListingApproved() { return topic(TntTopics.MARKET_LISTING_APPROVED); }
    @Bean public NewTopic tntMarketListingPublished() { return topic(TntTopics.MARKET_LISTING_PUBLISHED); }
    @Bean public NewTopic tntMarketListingRejected() { return topic(TntTopics.MARKET_LISTING_REJECTED); }
    @Bean public NewTopic tntMarketMerchantContractSigned() { return topic(TntTopics.MARKET_MERCHANT_CONTRACT_SIGNED); }
    @Bean public NewTopic tntMarketOrderCompleted() { return topic(TntTopics.MARKET_ORDER_COMPLETED); }
    @Bean public NewTopic tntMarketOrderCreated() { return topic(TntTopics.MARKET_ORDER_CREATED); }
    @Bean public NewTopic tntMarketOrderPaid() { return topic(TntTopics.MARKET_ORDER_PAID); }
    @Bean public NewTopic tntMarketProviderReviewPublished() { return topic(TntTopics.MARKET_PROVIDER_REVIEW_PUBLISHED); }
    @Bean public NewTopic tntMarketQuoteRequestCreated() { return topic(TntTopics.MARKET_QUOTE_REQUEST_CREATED); }
    @Bean public NewTopic tntMarketQuoteResponseSubmitted() { return topic(TntTopics.MARKET_QUOTE_RESPONSE_SUBMITTED); }
    @Bean public NewTopic tntProductCreated() { return topic(TntTopics.PRODUCT_CREATED); }
    @Bean public NewTopic tntProductOfferPublished() { return topic(TntTopics.PRODUCT_OFFER_PUBLISHED); }
    @Bean public NewTopic tntRouteEtaUpdated() { return topic(TntTopics.ROUTE_ETA_UPDATED); }
    @Bean public NewTopic tntRouteRerouteEvents() { return topic(TntTopics.ROUTE_REROUTE_EVENTS); }
    @Bean public NewTopic tntRouteTourEvents() { return topic(TntTopics.ROUTE_TOUR_EVENTS); }
    @Bean public NewTopic tntRouteVrpEvents() { return topic(TntTopics.ROUTE_VRP_EVENTS); }
    @Bean public NewTopic tntSalesOrderCancelled() { return topic(TntTopics.SALES_ORDER_CANCELLED); }
    @Bean public NewTopic tntSalesOrderConfirmed() { return topic(TntTopics.SALES_ORDER_CONFIRMED); }
    @Bean public NewTopic tntSalesOrderDelivered() { return topic(TntTopics.SALES_ORDER_DELIVERED); }
    @Bean public NewTopic tntSalesOrderDispatched() { return topic(TntTopics.SALES_ORDER_DISPATCHED); }
    @Bean public NewTopic tntTpClientProfileEvents() { return topic(TntTopics.TP_CLIENT_PROFILE_EVENTS); }
    @Bean public NewTopic tntTpKycEvents() { return topic(TntTopics.TP_KYC_EVENTS); }
    @Bean public NewTopic tntTpLoyaltyEvents() { return topic(TntTopics.TP_LOYALTY_EVENTS); }
    @Bean public NewTopic tntTpPhoneAliasEvents() { return topic(TntTopics.TP_PHONE_ALIAS_EVENTS); }
    @Bean public NewTopic tntTpRatingEvents() { return topic(TntTopics.TP_RATING_EVENTS); }
    @Bean public NewTopic tntTpUnknownEvents() { return topic(TntTopics.TP_UNKNOWN_EVENTS); }
    @Bean public NewTopic tntTrustEvents() { return topic(TntTopics.TRUST_EVENTS); }
    @Bean public NewTopic tntTrustEventsCommitted() { return topic(TntTopics.TRUST_EVENTS_COMMITTED); }

    // ── Geo Topics ────────────────────────────────────────────────────────────
    @Bean public NewTopic tntGeoAlertCreated()      { return topic(TntTopics.GEO_ALERT_CREATED); }

    // ── Organization Topics ───────────────────────────────────────────────────
    @Bean public NewTopic tntOrganizationHubUpdated() { return topic(TntTopics.ORGANIZATION_HUB_UPDATED); }

    // ── Realtime Topics ───────────────────────────────────────────────────────
    // tnt.realtime.gps.position.updated — enriched GPS events with anomaly flags
    // consumed by tnt-delivery-core (DeliveryWatchConsumer) to raise delivery alerts (lot L3, decision D.2).
    @Bean public NewTopic tntRealtimeGpsPositionUpdated() { return topic(TntTopics.REALTIME_GPS_POSITION_UPDATED); }
    // tnt.realtime.geofence.triggered — geofence breach events with zoneType field
    // consumed by tnt-delivery-core (DeliveryWatchConsumer): DANGER / RESTRICTED zones raise a delivery alert.
    @Bean public NewTopic tntRealtimeGeofenceTriggered()  { return topic(TntTopics.REALTIME_GEOFENCE_TRIGGERED); }
    @Bean public NewTopic tntLiveEtaUpdated()             { return topic(TntTopics.REALTIME_ETA_UPDATED); }

    // ── Notify Topics ─────────────────────────────────────────────────────────

    // ── Media Topics ──────────────────────────────────────────────────────────

    // ── Billing Topics ────────────────────────────────────────────────────────
    @Bean public NewTopic tntInvoiceEvents()     { return topic(TntTopics.BILLING_INVOICE_EVENTS); }
    @Bean public NewTopic tntInvoicePaid()       { return topic(TntTopics.BILLING_INVOICE_PAID); }
    @Bean public NewTopic tntPaymentConfirmed()  { return topic(TntTopics.BILLING_WALLET_PAYMENT_CONFIRMED); }  

    // ── Dispute Topics ────────────────────────────────────────────────────────
    @Bean public NewTopic tntBillingCompensationPaid()       { return topic(TntTopics.BILLING_COMPENSATION_PAID); }

    // Lot L8 (G.5): the per-event delivery, incident, dispute and hub topics are gone — see the aggregate topics above.

    // ── FreelancerOrg Topics () ──────────────────────────────────────────────

    /** FreelancerVehicle registered in tnt-resource-core — triggers fleet onboarding. */
    @Bean public NewTopic tntFreelancerVehicleRegistered() {
        return topic(TntTopics.RESOURCE_FREELANCER_VEHICLE_REGISTERED);
    }

    /**
     * FreelancerVehicle assigned to a delivery mission.
     * 6 partitions for high-throughput fleet tracking.
     * Consumed by tnt-delivery-core (FreelancerVehicleEventConsumer).
     */
    @Bean public NewTopic tntVehicleAssignedToMission() {
        return TopicBuilder.name(TntTopics.VEHICLE_ASSIGNED_TO_MISSION)
                .partitions(6)
                .replicas(replicationFactor)
                .build();
    }

    /**
     * FreelancerVehicle released from a delivery mission.
     * 6 partitions — matches vehicle assignment topic.
     */
    @Bean public NewTopic tntVehicleReleasedFromMission() {
        return TopicBuilder.name(TntTopics.VEHICLE_RELEASED_FROM_MISSION)
                .partitions(6)
                .replicas(replicationFactor)
                .build();
    }

    /**
     * Billing policy template applied to create a new policy.
     * Consumed by: tnt-notify-core, tnt-billing-report.
     */
    @Bean public NewTopic tntBillingTemplateApplied() {
        return topic(TntTopics.BILLING_TEMPLATE_APPLIED);
    }

    /**
     * Custom billing policy template saved by an actor.
     * Consumed by: tnt-billing-report (template usage analytics).
     */
    @Bean public NewTopic tntBillingCustomTemplateSaved() {
        return topic(TntTopics.BILLING_CUSTOM_TEMPLATE_SAVED);
    }

    // ── FreelancerOrg Admin Lifecycle Topics () ───────────────────────────

    /** FreelancerOrg KYC approved by a platform admin. */
    @Bean public NewTopic tntAdminFreelancerOrgKycApproved() {
        return topic(TntTopics.ADMIN_FREELANCER_ORG_KYC_APPROVED);
    }

    /** FreelancerOrg KYC rejected by a platform admin. */
    @Bean public NewTopic tntAdminFreelancerOrgKycRejected() {
        return topic(TntTopics.ADMIN_FREELANCER_ORG_KYC_REJECTED);
    }

    /** FreelancerOrg suspended by a platform admin. */
    @Bean public NewTopic tntAdminFreelancerOrgSuspended() {
        return topic(TntTopics.ADMIN_FREELANCER_ORG_SUSPENDED);
    }

    /** FreelancerOrg suspension lifted. */
    @Bean public NewTopic tntAdminFreelancerOrgUnsuspended() {
        return topic(TntTopics.ADMIN_FREELANCER_ORG_UNSUSPENDED);
    }

    /** FreelancerOrg permanently blacklisted. */
    @Bean public NewTopic tntAdminFreelancerOrgBlacklisted() {
        return topic(TntTopics.ADMIN_FREELANCER_ORG_BLACKLISTED);
    }

    // ── Link Topics (tnt-link-back-core, coreBackend) ─────────────────────────

    /**
     * Compacted topic — Kafka keeps only the latest record per key (nodeId), so it acts as a
     * durable, replayable "last known position" log rather than an event stream (Chantier G,
     * Audit n5 P-17). First compacted topic in this codebase — no existing bean to copy, so
     * {@code cleanup.policy=compact} is set explicitly instead of via the shared {@link #topic}
     * helper (which always builds a delete-policy topic).
     */
    @Bean
    public NewTopic tntLinkTrackingPositionLatest() {
        return TopicBuilder.name(TntTopics.LINK_TRACKING_POSITION_LATEST)
                .partitions(partitions)
                .replicas(replicationFactor)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .build();
    }

    // ── Sync Topics ───────────────────────────────────────────────────────────
    @Bean public NewTopic tntSyncConflictDetected()  { return topic(TntTopics.SYNC_CONFLICT_DETECTED); }

    // ── Actor / Identity Topics ───────────────────────────────────────────────
    @Bean public NewTopic tntActorProfileUpdated()    { return topic(TntTopics.ACTOR_PROFILE_UPDATED); }

    // ── Roles Topics ──────────────────────────────────────────────────────────
    @Bean public NewTopic tntRolesPermissionChanged() { return topic(TntTopics.ROLES_PERMISSION_CHANGED); }
    @Bean public NewTopic tntRolesPlatformAdminChanged() { return topic(TntTopics.ROLES_PLATFORM_ADMIN_CHANGED); }

    // ── Event Outbox (Dead Letter Queue) ──────────────────────────────────────
    @Bean public NewTopic tntDlq() { return topic(TntTopics.DLQ); }

    // ── Lot L8 (spec annex 9.1): topics produced under a literal name, now TntTopics constants ──
    @Bean public NewTopic tntNotificationsSent() { return topic(TntTopics.NOTIFY_NOTIFICATIONS_SENT); }
    @Bean public NewTopic tntNotificationsFailed() { return topic(TntTopics.NOTIFY_NOTIFICATIONS_FAILED); }
    @Bean public NewTopic tntFreelancerOrgSuspended() { return topic(TntTopics.FREELANCER_ORG_SUSPENDED); }
    @Bean public NewTopic tntFreelancerOrgKycUpgraded() { return topic(TntTopics.FREELANCER_ORG_KYC_UPGRADED); }
    @Bean public NewTopic tntActorFreelancerAssociated() { return topic(TntTopics.ACTOR_FREELANCER_ASSOCIATED); }
    @Bean public NewTopic tntActorFreelancerDissociated() { return topic(TntTopics.ACTOR_FREELANCER_DISSOCIATED); }
    @Bean public NewTopic tntActorFreelancerOrgLinked() { return topic(TntTopics.ACTOR_FREELANCER_ORG_LINKED); }
    @Bean public NewTopic tntActorFreelancerOrgUnlinked() { return topic(TntTopics.ACTOR_FREELANCER_ORG_UNLINKED); }
    @Bean public NewTopic tntResourceVehicleRegistered() { return topic(TntTopics.RESOURCE_VEHICLE_REGISTERED); }
    @Bean public NewTopic tntResourceVehicleAssigned() { return topic(TntTopics.RESOURCE_VEHICLE_ASSIGNED); }
    @Bean public NewTopic tntResourceVehicleUnassigned() { return topic(TntTopics.RESOURCE_VEHICLE_UNASSIGNED); }
    @Bean public NewTopic tntResourceVehicleMaintenance() { return topic(TntTopics.RESOURCE_VEHICLE_MAINTENANCE); }
    @Bean public NewTopic tntResourceVehicleRetired() { return topic(TntTopics.RESOURCE_VEHICLE_RETIRED); }
    @Bean public NewTopic tntResourceVehicleLocation() { return topic(TntTopics.RESOURCE_VEHICLE_LOCATION); }
    @Bean public NewTopic tntResourceMaintenanceAlert() { return topic(TntTopics.RESOURCE_MAINTENANCE_ALERT); }
    @Bean public NewTopic tntResourceEquipmentAssigned() { return topic(TntTopics.RESOURCE_EQUIPMENT_ASSIGNED); }
    // produced although annex 9.1 listed them as unused ("SUPPRIMÉ"): kept, now declared (session, sync batch)
    @Bean public NewTopic tntRealtimeActorConnected() { return topic(TntTopics.REALTIME_ACTOR_CONNECTED); }
    @Bean public NewTopic tntRealtimeActorDisconnected() { return topic(TntTopics.REALTIME_ACTOR_DISCONNECTED); }
    @Bean public NewTopic tntSyncCompleted() { return topic(TntTopics.SYNC_COMPLETED); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Aggregate topic: delete policy (never compacted — every change is an event), long retention. */
    private NewTopic aggregateTopic(String name) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicationFactor)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(aggregateRetentionMs))
                .build();
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }
}
