package com.yowyob.tiibntick.bootstrap.config;

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
 * <p> — Added all {@code tnt.incident.*} topics for {@code tnt-incident-core} (L3),
 * and {@code tnt.realtime.gps.position.updated} / {@code tnt.realtime.geofence.triggered}
 * consumed by the incident engine.
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
    @Bean public NewTopic tntMissionCreated()       { return topic("tnt.delivery.mission.created"); }
    @Bean public NewTopic tntMissionStatusChanged() { return topic("tnt.delivery.mission.status-changed"); }
    @Bean public NewTopic tntPackagePickedUp()      { return topic("tnt.delivery.package.picked-up"); }
    @Bean public NewTopic tntPackageDelivered()     { return topic("tnt.delivery.package.delivered"); }
    @Bean public NewTopic tntHubDepositCreated()    { return topic("tnt.delivery.hub-deposit.created"); }
    @Bean public NewTopic tntHubDepositPickedUp()   { return topic("tnt.delivery.hub-deposit.picked-up"); }

    // ── Geo Topics ────────────────────────────────────────────────────────────
    @Bean public NewTopic tntGpsPositionUpdated()   { return topic("tnt.geo.gps.position-updated"); }

    // ── Realtime Topics ───────────────────────────────────────────────────────
    // tnt.realtime.gps.position.updated — enriched GPS events with anomaly flags
    // consumed by tnt-incident-core (IncidentEventConsumer) to auto-detect incidents.
    @Bean public NewTopic tntRealtimeGpsPositionUpdated() { return topic("tnt.realtime.gps.position.updated"); }
    // tnt.realtime.geofence.triggered — geofence breach events with zoneType field
    // consumed by tnt-incident-core to auto-create geographic incidents.
    @Bean public NewTopic tntRealtimeGeofenceTriggered()  { return topic("tnt.realtime.geofence.triggered"); }
    @Bean public NewTopic tntLiveEtaUpdated()             { return topic("tnt.realtime.eta.updated"); }
    @Bean public NewTopic tntPresenceChanged()            { return topic("tnt.realtime.presence.changed"); }

    // ── Notify Topics ─────────────────────────────────────────────────────────
    @Bean public NewTopic tntNotificationRequested() { return topic("tnt.notify.notification.requested"); }
    @Bean public NewTopic tntNotificationDelivered() { return topic("tnt.notify.notification.delivered"); }
    @Bean public NewTopic tntNotificationFailed()    { return topic("tnt.notify.notification.failed"); }

    // ── Media Topics ──────────────────────────────────────────────────────────
    @Bean public NewTopic tntMediaUploaded()  { return topic("tnt.media.file.uploaded"); }
    @Bean public NewTopic tntMediaDeleted()   { return topic("tnt.media.file.deleted"); }

    // ── Billing Topics ────────────────────────────────────────────────────────
    @Bean public NewTopic tntInvoiceCreated()    { return topic("tnt.billing.invoice.created"); }
    @Bean public NewTopic tntPaymentConfirmed()  { return topic("tnt.billing.payment.confirmed"); }
    @Bean public NewTopic tntPaymentFailed()     { return topic("tnt.billing.payment.failed"); }
    @Bean public NewTopic tntWalletCredited()    { return topic("tnt.billing.wallet.credited"); }
    @Bean public NewTopic tntWalletDebited()     { return topic("tnt.billing.wallet.debited"); }

    // ── Dispute Topics ────────────────────────────────────────────────────────
    @Bean public NewTopic tntDisputeOpened()          { return topic("tnt.dispute.opened"); }
    @Bean public NewTopic tntDisputeResolved()         { return topic("tnt.dispute.resolved"); }
    @Bean public NewTopic tntDisputeEscalated()        { return topic("tnt.dispute.escalated"); }
    @Bean public NewTopic tntDisputeEvidenceAdded()    { return topic("tnt.dispute.evidence-added"); }
    @Bean public NewTopic tntDisputeRefundInitiated()  { return topic("tnt.dispute.refund-initiated"); }
    @Bean public NewTopic tntDisputeCompensationPaid() { return topic("tnt.dispute.compensation-paid"); }
    @Bean public NewTopic tntDisputeClosed()           { return topic("tnt.dispute.closed"); }

    // ── Incident Topics (tnt-incident-core L3) ────────────────────────────────
    // Published by tnt-incident-core IncidentKafkaEventPublisher

    /** Incident reported — initial detection event. */
    @Bean public NewTopic tntIncidentCreated()    { return topic("tnt.incident.created"); }

    /** Any incident state machine transition. */
    @Bean public NewTopic tntIncidentStatusChanged() { return topic("tnt.incident.status.changed"); }

    /** Incident triage completed — severity and risk score computed. */
    @Bean public NewTopic tntIncidentTriaged()    { return topic("tnt.incident.triaged"); }

    /** Replacement driver assigned by auto-resolution engine. */
    @Bean public NewTopic tntIncidentDriverAssigned() { return topic("tnt.incident.driver.assigned"); }

    /** Double-confirmed parcel handover between original and replacement driver. */
    @Bean public NewTopic tntIncidentHandoverCompleted() { return topic("tnt.incident.handover.completed"); }

    /** Incident resolved — SLA impact and compensation computed. */
    @Bean public NewTopic tntIncidentResolved()   { return topic("tnt.incident.resolved"); }

    /** Incident definitively closed — blockchain chain finalized. */
    @Bean public NewTopic tntIncidentClosed()     { return topic("tnt.incident.closed"); }

    /** Incident cancelled before resolution. */
    @Bean public NewTopic tntIncidentCancelled()  { return topic("tnt.incident.cancelled"); }

    /** Incident escalated up the management hierarchy. */
    @Bean public NewTopic tntIncidentEscalated()  { return topic("tnt.incident.escalated"); }

    /**
     * Incident crossed the fraud/damage threshold and converted to a formal dispute.
     * Consumed by {@code tnt-dispute-core} to auto-create a {@code Dispute}.
     */
    @Bean public NewTopic tntIncidentEscalatedToDispute() {
        return topic("tnt.incident.escalated.to.dispute");
    }

    /** Inter-agency cooperation requested to resolve the incident. */
    @Bean public NewTopic tntIncidentInteragencyRequested() {
        return topic("tnt.incident.interagency.requested");
    }

    /** Inter-agency cooperation completed. */
    @Bean public NewTopic tntIncidentInteragencyCompleted() {
        return topic("tnt.incident.interagency.completed");
    }

    // ── FreelancerOrg Topics () ──────────────────────────────────────────────

    /** FreelancerVehicle registered in tnt-resource-core — triggers fleet onboarding. */
    @Bean public NewTopic tntFreelancerVehicleRegistered() {
        return topic("tnt.resource.freelancer.vehicle.registered");
    }

    /**
     * FreelancerVehicle assigned to a delivery mission.
     * 6 partitions for high-throughput fleet tracking.
     * Consumed by tnt-delivery-core (FreelancerVehicleEventConsumer).
     */
    @Bean public NewTopic tntVehicleAssignedToMission() {
        return TopicBuilder.name("tnt.vehicle.assigned_to_mission")
                .partitions(6)
                .replicas(replicationFactor)
                .build();
    }

    /**
     * FreelancerVehicle released from a delivery mission.
     * 6 partitions — matches vehicle assignment topic.
     */
    @Bean public NewTopic tntVehicleReleasedFromMission() {
        return TopicBuilder.name("tnt.vehicle.released_from_mission")
                .partitions(6)
                .replicas(replicationFactor)
                .build();
    }

    /**
     * FreelancerOrg assigned to a delivery.
     * Consumed by: tnt-accounting-core, tnt-notify-core, tnt-billing-wallet.
     */
    @Bean public NewTopic tntDeliveryFreelancerOrgAssigned() {
        return topic("tnt.delivery.freelancer_org.assigned");
    }

    /**
     * Billing policy template applied to create a new policy.
     * Consumed by: tnt-notify-core, tnt-billing-report.
     */
    @Bean public NewTopic tntBillingTemplateApplied() {
        return topic("tnt.billing.template.applied");
    }

    /**
     * Custom billing policy template saved by an actor.
     * Consumed by: tnt-billing-report (template usage analytics).
     */
    @Bean public NewTopic tntBillingCustomTemplateSaved() {
        return topic("tnt.billing.custom_template.saved");
    }

    // ── FreelancerOrg Admin Lifecycle Topics () ───────────────────────────

    /** FreelancerOrg KYC approved by a platform admin. */
    @Bean public NewTopic tntAdminFreelancerOrgKycApproved() {
        return topic("tnt.admin.freelancer_org.kyc_approved");
    }

    /** FreelancerOrg KYC rejected by a platform admin. */
    @Bean public NewTopic tntAdminFreelancerOrgKycRejected() {
        return topic("tnt.admin.freelancer_org.kyc_rejected");
    }

    /** FreelancerOrg suspended by a platform admin. */
    @Bean public NewTopic tntAdminFreelancerOrgSuspended() {
        return topic("tnt.admin.freelancer_org.suspended");
    }

    /** FreelancerOrg suspension lifted. */
    @Bean public NewTopic tntAdminFreelancerOrgUnsuspended() {
        return topic("tnt.admin.freelancer_org.unsuspended");
    }

    /** FreelancerOrg permanently blacklisted. */
    @Bean public NewTopic tntAdminFreelancerOrgBlacklisted() {
        return topic("tnt.admin.freelancer_org.blacklisted");
    }

    /** Wallet payment split executed (platform + org + sub-deliverer distribution). */
    @Bean public NewTopic tntBillingWalletSplitExecuted() {
        return topic("tnt.billing.wallet.split_executed");
    }

    // ── Sync Topics ───────────────────────────────────────────────────────────
    @Bean public NewTopic tntSyncDeltaRequested()    { return topic("tnt.sync.delta.requested"); }
    @Bean public NewTopic tntSyncConflictDetected()  { return topic("tnt.sync.conflict.detected"); }

    // ── Actor / Identity Topics ───────────────────────────────────────────────
    @Bean public NewTopic tntActorProfileUpdated()    { return topic("tnt.actor.profile.updated"); }
    @Bean public NewTopic tntActorReputationChanged() { return topic("tnt.actor.reputation.changed"); }

    // ── Event Outbox (Dead Letter Queue) ──────────────────────────────────────
    @Bean public NewTopic tntDlq() { return topic("tnt.dlq"); }

    @Bean
    public NewTopic tntOutboxEvents() {
        // Single-partition outbox for transactional ordering guarantee
        return TopicBuilder.name("tnt.outbox.events")
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
