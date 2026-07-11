package com.yowyob.tiibntick.core.trust.domain.policy;

import com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType;

import java.util.Map;

/**
 * Domain Service — {@code LogisticEventCatalog}.
 *
 * <p>Canonical catalog of all TiiBnTick logistic trust events.
 * Maps each {@link LogisticTrustEventType} to:
 * <ul>
 *   <li>Its corresponding Trust Chaincode v2 function name</li>
 *   <li>A human-readable description</li>
 *   <li>The {@code entityType} string used in the Kernel's event record</li>
 * </ul>
 *
 * <p>This catalog is the single source of truth for the routing contract
 * between {@code tnt-trust} and {@code yow-trust-event}'s router.
 * It complements {@link LogisticTrustEvent#toKernelEventType()} by providing
 * additional metadata for monitoring, documentation, and audit UIs.
 *
 * <p><strong>v1.1:</strong> Added incident blockchain event catalog entries,
 * required by {@code tnt-incident-core}'s {@code IBlockchainAuditPort}.
 *
 * <p><strong>No Spring annotations.</strong> Pure domain code — stateless.
 *
 * @author MANFOUO Braun
 * @version 1.1
 */
public final class LogisticEventCatalog {

    /**
     * Immutable record representing the metadata of a catalogued event.
     *
     * @param chaincodeFunctionName the Trust Chaincode v2 function to invoke
     * @param entityType            the domain entity type string for Kernel routing
     * @param description           human-readable event description (in English)
     * @param version               catalog version this entry was introduced in
     */
    public record CatalogEntry(
            String chaincodeFunctionName,
            String entityType,
            String description,
            String version) {}

    /**
     * The complete catalog.
     * Key: {@link LogisticTrustEventType}
     * Value: {@link CatalogEntry}
     */
    public static final Map<LogisticTrustEventType, CatalogEntry> CATALOG =
            Map.ofEntries(

                    // ── Delivery & Package ─────────────────────────────────────────

                    Map.entry(LogisticTrustEventType.DELIVERY_PROOF_RECORDED,
                            new CatalogEntry("recordDeliveryProof", "DELIVERY_PROOF",
                                    "A delivery proof (photo + GPS + signature) was recorded for a package.",
                                    "1.0")),

                    Map.entry(LogisticTrustEventType.PACKAGE_CUSTODY_TRANSFERRED,
                            new CatalogEntry("recordCustodyTransfer", "CUSTODY_TRANSFER",
                                    "Package custody was transferred between two actors.",
                                    "1.0")),

                    Map.entry(LogisticTrustEventType.HUB_DEPOSIT_CONFIRMED,
                            new CatalogEntry("recordHubDeposit", "HUB_DEPOSIT",
                                    "Package was deposited at a relay hub and confirmed by the operator.",
                                    "1.0")),

                    Map.entry(LogisticTrustEventType.HUB_PICKUP_CONFIRMED,
                            new CatalogEntry("recordHubPickup", "HUB_PICKUP",
                                    "Package was picked up from a relay hub.",
                                    "1.0")),

                    Map.entry(LogisticTrustEventType.MISSION_CREATED_ON_CHAIN,
                            new CatalogEntry("recordMissionCreated", "MISSION",
                                    "A delivery mission was created and anchored on-chain.",
                                    "1.0")),

                    Map.entry(LogisticTrustEventType.MISSION_COMPLETED_ON_CHAIN,
                            new CatalogEntry("recordMissionCompleted", "MISSION",
                                    "A delivery mission was completed successfully.",
                                    "1.0")),

                    Map.entry(LogisticTrustEventType.MISSION_CANCELLED_ON_CHAIN,
                            new CatalogEntry("recordMissionCancelled", "MISSION",
                                    "A delivery mission was cancelled.",
                                    "1.0")),

                    // ── Identity & Reputation ──────────────────────────────────────

                    Map.entry(LogisticTrustEventType.DELIVERER_DID_ISSUED,
                            new CatalogEntry("issueDID", "DID_DOCUMENT",
                                    "A Decentralized Identifier (DID) was issued to a deliverer actor.",
                                    "1.0")),

                    Map.entry(LogisticTrustEventType.DELIVERER_DID_REVOKED,
                            new CatalogEntry("revokeDID", "DID_DOCUMENT",
                                    "A deliverer DID was revoked (key compromised or actor banned).",
                                    "1.0")),

                    Map.entry(LogisticTrustEventType.FREELANCER_ORG_DID_ISSUED,
                            new CatalogEntry("issueFreelancerOrgDID", "DID_DOCUMENT",
                                    "A Decentralized Identifier (DID) was issued to a FreelancerOrganization.",
                                    "1.1")),

                    Map.entry(LogisticTrustEventType.FREELANCER_ORG_DID_REVOKED,
                            new CatalogEntry("revokeFreelancerOrgDID", "DID_DOCUMENT",
                                    "A FreelancerOrg's DID was revoked (org dissolved, fraud, or suspended).",
                                    "1.1")),

                    Map.entry(LogisticTrustEventType.BADGE_AWARDED,
                            new CatalogEntry("recordBadge", "BADGE",
                                    "A reputation badge was awarded to an actor and anchored on-chain.",
                                    "1.0")),

                    Map.entry(LogisticTrustEventType.BADGE_REVOKED,
                            new CatalogEntry("revokeBadge", "BADGE",
                                    "A previously awarded reputation badge was revoked.",
                                    "1.0")),

                    // ── Geolocation ───────────────────────────────────────────────

                    Map.entry(LogisticTrustEventType.PROOF_OF_LOCATION_VERIFIED,
                            new CatalogEntry("recordLocationProof", "POL_EVENT",
                                    "A Proof-of-Location (PoL) was verified and anchored on-chain.",
                                    "1.0")),

                    Map.entry(LogisticTrustEventType.GEOFENCE_CROSSING_RECORDED,
                            new CatalogEntry("recordGeofenceCrossing", "GEOFENCE_EVENT",
                                    "A geofence crossing (entry/exit of delivery zone) was recorded.",
                                    "1.0")),

                    // ── Governance / DAO Zones ─────────────────────────────────────

                    Map.entry(LogisticTrustEventType.DAO_RULE_ACTIVATED,
                            new CatalogEntry("recordDaoRule", "DAO_RULE",
                                    "A DAO zone collective governance rule was activated on-chain.",
                                    "1.0")),

                    Map.entry(LogisticTrustEventType.DAO_PROPOSAL_VOTED,
                            new CatalogEntry("recordDaoVote", "DAO_VOTE",
                                    "A governance proposal received a vote from a zone member.",
                                    "1.0")),

                    Map.entry(LogisticTrustEventType.ZONE_MEMBERSHIP_RECORDED,
                            new CatalogEntry("recordZoneMembership", "ZONE_MEMBERSHIP",
                                    "An actor's membership in a DAO zone was recorded on-chain.",
                                    "1.0")),

                    // ── Billing ───────────────────────────────────────────────────

                    Map.entry(LogisticTrustEventType.BILLING_POLICY_ACTIVATED,
                            new CatalogEntry("recordBillingPolicy", "BILLING_POLICY",
                                    "A billing policy (tariff structure) was activated and anchored on-chain.",
                                    "1.0")),

                    Map.entry(LogisticTrustEventType.PRICING_RULE_CHANGED,
                            new CatalogEntry("recordPricingRuleChange", "PRICING_RULE",
                                    "A pricing rule was changed and the modification anchored on-chain.",
                                    "1.0")),

                    Map.entry(LogisticTrustEventType.PAYMENT_COMMITTED,
                            new CatalogEntry("recordPaymentCommit", "PAYMENT",
                                    "A payment transaction was committed to the ledger for audit.",
                                    "1.0")),

                    Map.entry(LogisticTrustEventType.DISPUTE_EVIDENCE_ANCHORED,
                            new CatalogEntry("recordDisputeEvidence", "DISPUTE_EVIDENCE",
                                    "A piece of dispute evidence was anchored on-chain for tamper-proof mediation.",
                                    "1.0")),

                    // ── Incident Blockchain (v1.1) ─────────────────────────────────

                    Map.entry(LogisticTrustEventType.INCIDENT_CREATED,
                            new CatalogEntry("recordIncidentCreated", "INCIDENT",
                                    "A delivery incident was created and anchored as the genesis block "
                                            + "of a dedicated incident chain.",
                                    "1.1")),

                    Map.entry(LogisticTrustEventType.INCIDENT_CHAIN_INITIALIZED,
                            new CatalogEntry("recordIncidentChainInit", "INCIDENT_CHAIN",
                                    "An incident blockchain chain was initialized for a multi-parcel incident.",
                                    "1.1")),

                    Map.entry(LogisticTrustEventType.EVIDENCE_ATTACHED,
                            new CatalogEntry("recordIncidentEvidence", "INCIDENT_EVIDENCE",
                                    "A piece of evidence was attached to an incident and anchored on-chain.",
                                    "1.1")),

                    Map.entry(LogisticTrustEventType.PARCEL_HANDOVER_COMPLETED,
                            new CatalogEntry("recordParcelHandover", "INCIDENT_HANDOVER",
                                    "A parcel handover between deliverers was completed and anchored on-chain.",
                                    "1.1")),

                    Map.entry(LogisticTrustEventType.INTER_AGENCY_COOPERATION_COMPLETED,
                            new CatalogEntry("recordInterAgencyCooperation", "INCIDENT_COOPERATION",
                                    "An inter-agency cooperation was completed and anchored on-chain.",
                                    "1.1")),

                    Map.entry(LogisticTrustEventType.INCIDENT_CLOSED,
                            new CatalogEntry("recordIncidentClosed", "INCIDENT",
                                    "An incident was definitively closed — terminal block in its chain.",
                                    "1.1")),

                    Map.entry(LogisticTrustEventType.PARCEL_CHAIN_RESUMED,
                            new CatalogEntry("recordParcelChainResumed", "PARCEL_CHAIN",
                                    "A parcel chain linked to an incident chain was resumed after resolution.",
                                    "1.1"))
            );

    /** Private constructor — utility class, not instantiable. */
    private LogisticEventCatalog() {}

    /**
     * Returns the {@link CatalogEntry} for the given event type.
     *
     * @param type the logistic trust event type
     * @return the corresponding catalog entry
     * @throws IllegalArgumentException if the type has no catalog entry
     */
    public static CatalogEntry getEntry(final LogisticTrustEventType type) {
        final CatalogEntry entry = CATALOG.get(type);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "No catalog entry for LogisticTrustEventType: " + type);
        }
        return entry;
    }

    /**
     * Returns the Trust Chaincode v2 function name for the given event type.
     *
     * @param type the logistic trust event type
     * @return the chaincode function name string
     */
    public static String getChaincodeFunction(final LogisticTrustEventType type) {
        return getEntry(type).chaincodeFunctionName();
    }

    /**
     * Returns the {@code entityType} string for the given event type.
     *
     * @param type the logistic trust event type
     * @return the entity type string
     */
    public static String getEntityType(final LogisticTrustEventType type) {
        return getEntry(type).entityType();
    }
}
