package com.yowyob.tiibntick.core.notify.domain.enums;

/**
 * Business notification types supportsd by tnt-notify-core.
 *
 * <p>
 * Groups notification semantics into logical domains (DELIVERY, INCIDENT,
 * BILLING, DISPUTE, SYSTEM). Each type maps to a specific i18n message template
 * key used by the translation layer.
 * </p>
 *
 * <p>
 * Incident notification types (prefix {@code INCIDENT_*}) are added to support
 * the tnt-incident-core module, which requires real-time alerting for:
 * incident creation, driver reassignment, passation handover, inter-agency
 * cooperation requests, and escalation events.
 * </p>
 *
 * @author MANFOUO Braun
 */
public enum NotificationType {

    // ── Delivery domain ───────────────────────────────────────────────────────

    /** Mission assigned to a deliverer. */
    MISSION_ASSIGNED,

    /** Mission status changed (IN_TRANSIT, DELIVERED, etc.). */
    MISSION_STATUS_CHANGED,

    /** Package delivered successfully — recipient notification. */
    PACKAGE_DELIVERED,

    /** ETA update for client tracking. */
    ETA_UPDATED,

    /** Pickup reminder — package ready at relay hub. */
    PICKUP_REMINDER,

    // ── Incident domain ───────────────────────────────────────────────────────

    /**
     * A new incident has been reported on a mission.
     * Recipients: agency manager, dispatching operator.
     */
    INCIDENT_CREATED,

    /**
     * An ongoing incident has been escalated to the agency hierarchy.
     * Recipients: branch manager, agency manager.
     */
    INCIDENT_ESCALATED,

    /**
     * A replacement driver has been proposed for a blocked mission.
     * Recipients: proposed driver (accept/refuse prompt), agency manager.
     */
    INCIDENT_DRIVER_PROPOSAL,

    /**
     * Physical handover of parcels between the original and replacement driver
     * has been confirmed by both parties.
     * Recipients: agency manager, mission client.
     */
    HANDOVER_CONFIRMED,

    /**
     * A replacement driver has been assigned by the agency and the mission
     * has been re-dispatched.
     * Recipients: replacement driver (mission details), client (new ETA).
     */
    DRIVER_MISSION_ASSIGNED,

    /**
     * The agency has formally started handling a reported incident internally.
     * Recipients: original driver (status update), client (reassurance
     * notification).
     */
    AGENCY_HANDLING_STARTED,

    /**
     * An inter-agency cooperation request has been sent.
     * Recipients: target agency manager.
     */
    INTERAGENCY_COOP_REQUESTED,

    /**
     * The status of an inter-agency cooperation has been updated.
     * Recipients: requesting agency manager.
     */
    INTERAGENCY_COOP_UPDATED,

    /**
     * The automated incident resolution process has failed.
     * Recipients: agency manager, platform support.
     */
    INCIDENT_AUTO_FAILED,

    /**
     * An incident has been formally closed after resolution.
     * Recipients: all involved parties.
     */
    INCIDENT_CLOSED,

    // ── Billing domain ────────────────────────────────────────────────────────

    /** Invoice generated. */
    INVOICE_GENERATED,

    /** Payment confirmed by Mobile Money provider. */
    PAYMENT_CONFIRMED,

    /** Payment failed. */
    PAYMENT_FAILED,

    /** Commission credited to deliverer wallet. */
    COMMISSION_CREDITED,

    // ── Dispute domain ────────────────────────────────────────────────────────

    /** New dispute opened. */
    DISPUTE_OPENED,

    /** Dispute ruled in favour of one party. */
    DISPUTE_RULED,

    /** Compensation processed and credited. */
    COMPENSATION_PROCESSED,

    // ── System domain ─────────────────────────────────────────────────────────

    /** Account KYC approved. */
    KYC_APPROVED,

    /** KYC rejected. */
    KYC_REJECTED,

    /** Security alert. */
    SECURITY_ALERT,

    /** Platform maintenance notice. */
    MAINTENANCE_NOTICE,

    // ── FreelancerOrg domain () ───────────────────────────────────────────

    /**
     * A FreelancerOrganization has been verified (KYC approved at any level).
     * Recipients: FreelancerOrg OWNER.
     * Channels: Push + SMS + Email.
     */
    FREELANCER_ORG_VERIFIED,

    /**
     * A FreelancerOrganization has been suspended by the platform admin.
     * Recipients: FreelancerOrg OWNER.
     * Channels: Push + SMS + Email.
     */
    FREELANCER_ORG_SUSPENDED,

    /**
     * A deliverer has been invited to join a FreelancerOrg as a sub-deliverer.
     * Recipients: The invited SUB_DELIVERER actor.
     * Channels: Push + SMS.
     */
    SUB_DELIVERER_INVITED,

    /**
     * A sub-deliverer has accepted an invitation to join a FreelancerOrg.
     * Recipients: FreelancerOrg OWNER.
     * Channels: Push.
     */
    SUB_DELIVERER_INVITATION_ACCEPTED,

    /**
     * A mission has been assigned to a specific sub-deliverer within a
     * FreelancerOrg.
     * Recipients: The assigned SUB_DELIVERER.
     * Channels: Push + SMS.
     */
    SUB_DELIVERER_MISSION_ASSIGNED,

    /**
     * The KYC level of a FreelancerOrg has been upgraded (BASIC → FULL).
     * Recipients: FreelancerOrg OWNER.
     * Channels: Push + Email.
     */
    KYC_LEVEL_UPGRADED,

    /**
     * A billing policy template has been applied to create a new billing policy.
     * Recipients: The actor who applied the template (any type).
     * Channels: Push (in-app confirmation).
     */
    BILLING_POLICY_TEMPLATE_APPLIED,

    /**
     * A billing surcharge was triggered for a delivery (optional client
     * transparency notification).
     * Recipients: The client (SENDER or payer).
     * Channels: Push (optional — configurable via preferences).
     */
    SURCHARGE_TRIGGERED
}
