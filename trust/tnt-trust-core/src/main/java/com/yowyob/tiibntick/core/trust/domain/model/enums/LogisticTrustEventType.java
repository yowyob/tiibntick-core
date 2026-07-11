package com.yowyob.tiibntick.core.trust.domain.model.enums;

/**
 * Enum — {@code LogisticTrustEventType}.
 *
 * <p>Complete catalog of all TiiBnTick logistic domain events eligible for
 * immutable anchoring on the Hyperledger Fabric ledger via {@code yow-trust-event}.
 *
 * <p>Each value maps to a specific chaincode function in the Trust Chaincode v2.
 * The mapping is performed by {@link com.yowyob.tiibntick.core.trust.application.service.LogisticEventPublisherService}.
 *
 * <p><strong>v1.1 — Incident blockchain event types added</strong> to support
 * the {@code IBlockchainAuditPort} required by {@code tnt-incident-core}.
 * Each incident event anchors a block in the incident-dedicated chain
 * (prefixed {@code INC-}) maintained locally in {@code tnt_trust.incident_blockchain_records}.
 *
 * @author MANFOUO Braun
 * @version 1.1
 */
public enum LogisticTrustEventType {

    // ── Delivery & Package ────────────────────────────────────────────────────

    /**
     * A delivery proof (photo, GPS coordinates, recipient signature) was recorded.
     * Triggers chaincode function: {@code recordDeliveryProof}
     */
    DELIVERY_PROOF_RECORDED,

    /**
     * Custody of a package was transferred between two actors
     * (e.g., deliverer → hub operator, hub → recipient).
     * Triggers chaincode function: {@code recordCustodyTransfer}
     */
    PACKAGE_CUSTODY_TRANSFERRED,

    /**
     * A package was deposited at a relay hub and confirmed by the hub operator.
     * Triggers chaincode function: {@code recordHubDeposit}
     */
    HUB_DEPOSIT_CONFIRMED,

    /**
     * A package was picked up from a relay hub.
     * Triggers chaincode function: {@code recordHubPickup}
     */
    HUB_PICKUP_CONFIRMED,

    /**
     * A delivery mission was created and anchored on-chain.
     * Provides immutable proof of mission existence before execution.
     * Triggers chaincode function: {@code recordMissionCreated}
     */
    MISSION_CREATED_ON_CHAIN,

    /**
     * A delivery mission was completed successfully — all packages delivered.
     * Triggers chaincode function: {@code recordMissionCompleted}
     */
    MISSION_COMPLETED_ON_CHAIN,

    /**
     * A delivery mission was cancelled and the cancellation anchored on-chain.
     * Triggers chaincode function: {@code recordMissionCancelled}
     */
    MISSION_CANCELLED_ON_CHAIN,

    // ── Actor Identity & Reputation ───────────────────────────────────────────

    /**
     * A Decentralized Identifier (DID) was issued to a deliverer actor.
     * The DID is verifiable without a central authority.
     * Triggers chaincode function: {@code issueDID}
     */
    DELIVERER_DID_ISSUED,

    /**
     * A previously issued DID was revoked (e.g., actor banned, key compromised).
     * Triggers chaincode function: {@code revokeDID}
     */
    DELIVERER_DID_REVOKED,

    // ── FreelancerOrg DID () ──────────────────────────────────────────────

    /**
     * A Decentralized Identifier was issued to a FreelancerOrganization ().
     * DID format: {@code did:tiibntick:{tenantId}:org:{orgId}}.
     * Provides verifiable proof of the FreelancerOrg's identity for cross-platform trust.
     * Triggers chaincode function: {@code issueFreelancerOrgDID}
     */
    FREELANCER_ORG_DID_ISSUED,

    /**
     * A FreelancerOrg's DID was revoked (org dissolved, fraud, or suspended).
     * Triggers chaincode function: {@code revokeFreelancerOrgDID}
     */
    FREELANCER_ORG_DID_REVOKED,

    /**
     * A reputation badge was earned by an actor (e.g., "100 Deliveries",
     * "Top Rated", "Certified Relay Operator") and anchored on-chain.
     * Triggers chaincode function: {@code recordBadge}
     */
    BADGE_AWARDED,

    /**
     * A previously earned badge was revoked.
     * Triggers chaincode function: {@code revokeBadge}
     */
    BADGE_REVOKED,

    // ── Geolocation ───────────────────────────────────────────────────────────

    /**
     * A Proof-of-Location (PoL) was verified and anchored on-chain.
     * Used to prove that a deliverer was physically at a delivery location
     * at a specific time.
     * Triggers chaincode function: {@code recordLocationProof}
     */
    PROOF_OF_LOCATION_VERIFIED,

    /**
     * A geofence crossing event was recorded on-chain
     * (e.g., deliverer entering/leaving a delivery zone).
     * Triggers chaincode function: {@code recordGeofenceCrossing}
     */
    GEOFENCE_CROSSING_RECORDED,

    // ── Governance / DAO Zones ────────────────────────────────────────────────

    /**
     * A DAO zone collective rule was created and activated on-chain.
     * Rules govern deliverer behavior in a geographic zone (e.g., pricing caps).
     * Triggers chaincode function: {@code recordDaoRule}
     */
    DAO_RULE_ACTIVATED,

    /**
     * A governance proposal was voted on by zone members.
     * Triggers chaincode function: {@code recordDaoVote}
     */
    DAO_PROPOSAL_VOTED,

    /**
     * An actor's zone membership was recorded on-chain.
     * Triggers chaincode function: {@code recordZoneMembership}
     */
    ZONE_MEMBERSHIP_RECORDED,

    // ── Facturation & Billing ─────────────────────────────────────────────────

    /**
     * A billing policy (tariff structure) was activated and anchored on-chain.
     * Provides immutable proof of the pricing terms in effect for a tenant.
     * Triggers chaincode function: {@code recordBillingPolicy}
     */
    BILLING_POLICY_ACTIVATED,

    /**
     * A pricing rule changed and the change was anchored on-chain.
     * Triggers chaincode function: {@code recordPricingRuleChange}
     */
    PRICING_RULE_CHANGED,

    /**
     * A payment transaction was committed to the ledger for audit purposes.
     * Triggers chaincode function: {@code recordPaymentCommit}
     */
    PAYMENT_COMMITTED,

    // ── Disputes ──────────────────────────────────────────────────────────────

    /**
     * A piece of dispute evidence (photo, GPS trace, delivery proof reference)
     * was anchored on-chain for tamper-proof mediation.
     * Triggers chaincode function: {@code recordDisputeEvidence}
     */
    DISPUTE_EVIDENCE_ANCHORED,

    // ── Incident Blockchain (v1.1 — required by tnt-incident-core IBlockchainAuditPort) ─────

    /**
     * A delivery incident was created and anchored as the genesis block
     * of a dedicated incident chain (INC-{chainId}).
     * Triggers chaincode function: {@code recordIncidentCreated}
     */
    INCIDENT_CREATED,

    /**
     * An incident blockchain chain was initialized (genesis block).
     * Used when a multi-parcel incident creates its own dedicated chain.
     * Triggers chaincode function: {@code recordIncidentChainInit}
     */
    INCIDENT_CHAIN_INITIALIZED,

    /**
     * A piece of evidence (photo, video, GPS trace, OTP signature) was
     * attached to an incident and anchored on-chain.
     * Triggers chaincode function: {@code recordIncidentEvidence}
     */
    EVIDENCE_ATTACHED,

    /**
     * A parcel handover between the original deliverer and a replacement
     * deliverer was completed, confirmed by both parties, and anchored on-chain.
     * Triggers chaincode function: {@code recordParcelHandover}
     */
    PARCEL_HANDOVER_COMPLETED,

    /**
     * An inter-agency cooperation operation (sharing a vehicle, driver, or hub)
     * was completed and anchored on-chain.
     * Triggers chaincode function: {@code recordInterAgencyCooperation}
     */
    INTER_AGENCY_COOPERATION_COMPLETED,

    /**
     * An incident was definitively closed and the closure anchored on-chain.
     * This is the terminal block in the incident chain.
     * Triggers chaincode function: {@code recordIncidentClosed}
     */
    INCIDENT_CLOSED,

    /**
     * After incident resolution, a parcel chain that was linked to the incident
     * chain was resumed. This anchors the reconnection point.
     * Triggers chaincode function: {@code recordParcelChainResumed}
     */
    PARCEL_CHAIN_RESUMED
}
