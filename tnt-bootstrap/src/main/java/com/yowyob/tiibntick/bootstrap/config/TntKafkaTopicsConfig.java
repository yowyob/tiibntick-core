package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.common.kafka.TntTopics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
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

    // ── Delivery Topics ────────────────────────────────────────────────────────
    @Bean public NewTopic tntMissionCreated()       { return topic(TntTopics.DELIVERY_MISSION_CREATED); }
    @Bean public NewTopic tntMissionStatusChanged() { return topic(TntTopics.DELIVERY_MISSION_STATUS_CHANGED); }
    @Bean public NewTopic tntMissionStarted()       { return topic(TntTopics.DELIVERY_MISSION_STARTED); }
    @Bean public NewTopic tntMissionCompleted()     { return topic(TntTopics.DELIVERY_MISSION_COMPLETED); }
    @Bean public NewTopic tntMissionFailed()        { return topic(TntTopics.DELIVERY_MISSION_FAILED); }
    @Bean public NewTopic tntPackagePickedUp()      { return topic(TntTopics.DELIVERY_PACKAGE_PICKED_UP); }
    @Bean public NewTopic tntPackageDelivered()     { return topic(TntTopics.DELIVERY_PACKAGE_DELIVERED); }
    @Bean public NewTopic tntPackageUpdated()       { return topic(TntTopics.DELIVERY_PACKAGE_UPDATED); }
    @Bean public NewTopic tntHubDepositCreated()    { return topic(TntTopics.DELIVERY_HUB_DEPOSIT_CREATED); }
    @Bean public NewTopic tntHubDepositPickedUp()   { return topic(TntTopics.DELIVERY_HUB_DEPOSIT_PICKED_UP); }

    // ── Geo Topics ────────────────────────────────────────────────────────────
    @Bean public NewTopic tntGpsPositionUpdated()   { return topic(TntTopics.GEO_GPS_POSITION_UPDATED); }
    @Bean public NewTopic tntGeoAlertCreated()      { return topic(TntTopics.GEO_ALERT_CREATED); }

    // ── Organization Topics ───────────────────────────────────────────────────
    @Bean public NewTopic tntOrganizationHubUpdated() { return topic(TntTopics.ORGANIZATION_HUB_UPDATED); }

    // ── Realtime Topics ───────────────────────────────────────────────────────
    // tnt.realtime.gps.position.updated — enriched GPS events with anomaly flags
    // consumed by tnt-incident-core (IncidentEventConsumer) to auto-detect incidents.
    @Bean public NewTopic tntRealtimeGpsPositionUpdated() { return topic(TntTopics.REALTIME_GPS_POSITION_UPDATED); }
    // tnt.realtime.geofence.triggered — geofence breach events with zoneType field
    // consumed by tnt-incident-core to auto-create geographic incidents.
    @Bean public NewTopic tntRealtimeGeofenceTriggered()  { return topic(TntTopics.REALTIME_GEOFENCE_TRIGGERED); }
    @Bean public NewTopic tntLiveEtaUpdated()             { return topic(TntTopics.REALTIME_ETA_UPDATED); }
    @Bean public NewTopic tntPresenceChanged()            { return topic(TntTopics.REALTIME_PRESENCE_CHANGED); }

    // ── Notify Topics ─────────────────────────────────────────────────────────
    @Bean public NewTopic tntNotificationRequested() { return topic(TntTopics.NOTIFY_NOTIFICATION_REQUESTED); }
    @Bean public NewTopic tntNotificationDelivered() { return topic(TntTopics.NOTIFY_NOTIFICATION_DELIVERED); }
    @Bean public NewTopic tntNotificationFailed()    { return topic(TntTopics.NOTIFY_NOTIFICATION_FAILED); }

    // ── Media Topics ──────────────────────────────────────────────────────────
    @Bean public NewTopic tntMediaUploaded()  { return topic(TntTopics.MEDIA_FILE_UPLOADED); }
    @Bean public NewTopic tntMediaDeleted()   { return topic(TntTopics.MEDIA_FILE_DELETED); }

    // ── Billing Topics ────────────────────────────────────────────────────────
    @Bean public NewTopic tntInvoiceEvents()     { return topic(TntTopics.BILLING_INVOICE_EVENTS); }
    @Bean public NewTopic tntInvoicePaid()       { return topic(TntTopics.BILLING_INVOICE_PAID); }
    @Bean public NewTopic tntInvoiceCreated()    { return topic(TntTopics.BILLING_INVOICE_CREATED); }
    @Bean public NewTopic tntPaymentConfirmed()  { return topic(TntTopics.BILLING_WALLET_PAYMENT_CONFIRMED); }
    @Bean public NewTopic tntPaymentFailed()     { return topic(TntTopics.BILLING_PAYMENT_FAILED); }
    @Bean public NewTopic tntWalletCredited()    { return topic(TntTopics.BILLING_WALLET_CREDITED_PROVISIONING_BEAN); }
    @Bean public NewTopic tntWalletDebited()     { return topic(TntTopics.BILLING_WALLET_DEBITED_PROVISIONING_BEAN); }

    // ── Dispute Topics ────────────────────────────────────────────────────────
    @Bean public NewTopic tntDisputeOpened()          { return topic(TntTopics.DISPUTE_OPENED); }
    @Bean public NewTopic tntDisputeResolved()         { return topic(TntTopics.DISPUTE_RESOLVED); }
    @Bean public NewTopic tntDisputeEscalated()        { return topic(TntTopics.DISPUTE_ESCALATED); }
    @Bean public NewTopic tntDisputeEvidenceAdded()    { return topic(TntTopics.DISPUTE_EVIDENCE_ADDED); }
    @Bean public NewTopic tntDisputeRefundInitiated()  { return topic(TntTopics.DISPUTE_REFUND_INITIATED); }
    @Bean public NewTopic tntDisputeCompensationPaid() { return topic(TntTopics.DISPUTE_COMPENSATION_PAID); }
    @Bean public NewTopic tntDisputeClosed()           { return topic(TntTopics.DISPUTE_CLOSED); }
    @Bean public NewTopic tntBillingCompensationInitiated() { return topic(TntTopics.BILLING_COMPENSATION_INITIATED); }
    @Bean public NewTopic tntBillingCompensationPaid()       { return topic(TntTopics.BILLING_COMPENSATION_PAID); }

    // ── Incident Topics (tnt-incident-core L3) ────────────────────────────────
    // Published by tnt-incident-core IncidentKafkaEventPublisher

    /** Incident reported — initial detection event. */
    @Bean public NewTopic tntIncidentCreated()    { return topic(TntTopics.INCIDENT_CREATED); }

    /** Any incident state machine transition. */
    @Bean public NewTopic tntIncidentStatusChanged() { return topic(TntTopics.INCIDENT_STATUS_CHANGED); }

    /** Incident triage completed — severity and risk score computed. */
    @Bean public NewTopic tntIncidentTriaged()    { return topic(TntTopics.INCIDENT_TRIAGED); }

    /** Replacement driver assigned by auto-resolution engine. */
    @Bean public NewTopic tntIncidentDriverAssigned() { return topic(TntTopics.INCIDENT_DRIVER_ASSIGNED); }

    /** Double-confirmed parcel handover between original and replacement driver. */
    @Bean public NewTopic tntIncidentHandoverCompleted() { return topic(TntTopics.INCIDENT_HANDOVER_COMPLETED); }

    /** Incident resolved — SLA impact and compensation computed. */
    @Bean public NewTopic tntIncidentResolved()   { return topic(TntTopics.INCIDENT_RESOLVED); }

    /** Incident definitively closed — blockchain chain finalized. */
    @Bean public NewTopic tntIncidentClosed()     { return topic(TntTopics.INCIDENT_CLOSED); }

    /** Incident cancelled before resolution. */
    @Bean public NewTopic tntIncidentCancelled()  { return topic(TntTopics.INCIDENT_CANCELLED); }

    /** Incident escalated up the management hierarchy. */
    @Bean public NewTopic tntIncidentEscalated()  { return topic(TntTopics.INCIDENT_ESCALATED); }

    /**
     * Incident crossed the fraud/damage threshold and converted to a formal dispute.
     * Consumed by {@code tnt-dispute-core} to auto-create a {@code Dispute}.
     */
    @Bean public NewTopic tntIncidentEscalatedToDispute() {
        return topic(TntTopics.INCIDENT_ESCALATED_TO_DISPUTE);
    }

    /** Inter-agency cooperation requested to resolve the incident. */
    @Bean public NewTopic tntIncidentInteragencyRequested() {
        return topic(TntTopics.INCIDENT_INTERAGENCY_REQUESTED);
    }

    /** Inter-agency cooperation completed. */
    @Bean public NewTopic tntIncidentInteragencyCompleted() {
        return topic(TntTopics.INCIDENT_INTERAGENCY_COMPLETED);
    }

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
     * FreelancerOrg assigned to a delivery.
     * Consumed by: tnt-accounting-core, tnt-notify-core, tnt-billing-wallet.
     */
    @Bean public NewTopic tntDeliveryFreelancerOrgAssigned() {
        return topic(TntTopics.DELIVERY_FREELANCER_ORG_ASSIGNED);
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

    /** Wallet payment split executed (platform + org + sub-deliverer distribution). */
    @Bean public NewTopic tntBillingWalletSplitExecuted() {
        return topic(TntTopics.BILLING_WALLET_SPLIT_EXECUTED);
    }

    // ── Sync Topics ───────────────────────────────────────────────────────────
    @Bean public NewTopic tntSyncDeltaRequested()    { return topic(TntTopics.SYNC_DELTA_REQUESTED); }
    @Bean public NewTopic tntSyncConflictDetected()  { return topic(TntTopics.SYNC_CONFLICT_DETECTED); }
    @Bean public NewTopic tntSyncEntityChangedDlq()  { return topic(TntTopics.SYNC_ENTITY_CHANGED_DLQ); }

    // ── Actor / Identity Topics ───────────────────────────────────────────────
    @Bean public NewTopic tntActorProfileUpdated()    { return topic(TntTopics.ACTOR_PROFILE_UPDATED); }
    @Bean public NewTopic tntActorReputationChanged() { return topic(TntTopics.ACTOR_REPUTATION_CHANGED); }

    // ── Roles Topics ──────────────────────────────────────────────────────────
    @Bean public NewTopic tntRolesPermissionChanged() { return topic(TntTopics.ROLES_PERMISSION_CHANGED); }

    // ── Event Outbox (Dead Letter Queue) ──────────────────────────────────────
    @Bean public NewTopic tntDlq() { return topic(TntTopics.DLQ); }

    @Bean
    public NewTopic tntOutboxEvents() {
        // Single-partition outbox for transactional ordering guarantee
        return TopicBuilder.name(TntTopics.OUTBOX_EVENTS)
                .partitions(1)
                .replicas(replicationFactor)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }
}
