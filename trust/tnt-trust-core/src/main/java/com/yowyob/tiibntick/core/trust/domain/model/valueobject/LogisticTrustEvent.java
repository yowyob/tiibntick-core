package com.yowyob.tiibntick.core.trust.domain.model.valueobject;

import com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object — {@code LogisticTrustEvent}.
 *
 * <p>TiiBnTick-specific specialization of the Kernel's {@code TrustEvent} concept.
 * Carries all logistic domain context (missionId, packageTrackingCode, actorId,
 * hubId, GPS coordinates, PoL hash) that is meaningful within TiiBnTick but
 * opaque to the Kernel's {@code yow-trust-event} microservice.
 *
 * <p>Before being published to Kafka, a {@link LogisticTrustEvent} is converted
 * to a Kernel {@code TrustEventKafkaMessage} via {@link #toKafkaPayload()}.
 * The logistic-specific fields are serialized into the {@code payload} field.
 *
 * <h3>Factory Methods</h3>
 * <p>Each factory method represents a specific logistic domain scenario.
 * They enforce that all mandatory context for that event type is provided.
 *
 * <p><strong>No Spring annotations.</strong> Pure domain code.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public final class LogisticTrustEvent {

    /** Solution code for TiiBnTick — used as the {@code solutionCode} in yow-trust-event. */
    public static final String SOLUTION_CODE = "TNT";

    /** Source service identifier for all events published by this module. */
    public static final String SOURCE_SERVICE = "tnt-trust";

    // ── Kernel fields ─────────────────────────────────────────────────────────
    private final String correlationId;
    private final String tenantId;
    private final LogisticTrustEventType logisticEventType;
    private final String entityType;
    private final String entityId;

    // ── TiiBnTick-specific logistic context ───────────────────────────────────
    private final String missionId;
    private final String packageTrackingCode;
    private final String actorId;
    private final String hubId;
    private final Double gpsLat;
    private final Double gpsLng;

    /**
     * SHA-256 hash of the Proof-of-Location payload from the mobile client.
     * Null for non-geolocation event types.
     */
    private final String polHash;

    private final LocalDateTime occurredAt;

    /** JSON-serialized additional data specific to the event type. */
    private final String additionalData;

    private LogisticTrustEvent(
            final String correlationId,
            final String tenantId,
            final LogisticTrustEventType logisticEventType,
            final String entityType,
            final String entityId,
            final String missionId,
            final String packageTrackingCode,
            final String actorId,
            final String hubId,
            final Double gpsLat,
            final Double gpsLng,
            final String polHash,
            final LocalDateTime occurredAt,
            final String additionalData) {
        this.correlationId = Objects.requireNonNull(correlationId);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.logisticEventType = Objects.requireNonNull(logisticEventType);
        this.entityType = Objects.requireNonNull(entityType);
        this.entityId = Objects.requireNonNull(entityId);
        this.missionId = missionId;
        this.packageTrackingCode = packageTrackingCode;
        this.actorId = actorId;
        this.hubId = hubId;
        this.gpsLat = gpsLat;
        this.gpsLng = gpsLng;
        this.polHash = polHash;
        this.occurredAt = Objects.requireNonNull(occurredAt);
        this.additionalData = additionalData;
    }

    // ── Factory Methods ───────────────────────────────────────────────────────

    /**
     * Creates a {@link LogisticTrustEvent} for a delivery proof recording.
     *
     * @param proof     the delivery proof record to anchor
     * @param missionId the delivery mission identifier
     * @param actorId   the deliverer who recorded the proof
     */
    public static LogisticTrustEvent forDeliveryProof(
            final DeliveryProofRecord proof,
            final String missionId,
            final String actorId) {
        Objects.requireNonNull(proof);
        final String payload = buildDeliveryProofPayload(proof);
        return new LogisticTrustEvent(
                UUID.randomUUID().toString(),
                proof.getTenantId(),
                LogisticTrustEventType.DELIVERY_PROOF_RECORDED,
                "DELIVERY_PROOF",
                proof.getProofId(),
                missionId, proof.getPackageId(), actorId, null,
                proof.getGpsLat(), proof.getGpsLng(), null,
                proof.getConfirmedAt() != null ? proof.getConfirmedAt() : LocalDateTime.now(),
                payload);
    }

    /**
     * Creates a {@link LogisticTrustEvent} for a package custody transfer.
     *
     * @param transfer the custody transfer record to anchor
     */
    public static LogisticTrustEvent forCustodyTransfer(final CustodyTransferRecord transfer) {
        Objects.requireNonNull(transfer);
        final String payload = buildCustodyPayload(transfer);
        return new LogisticTrustEvent(
                UUID.randomUUID().toString(),
                transfer.getTenantId(),
                LogisticTrustEventType.PACKAGE_CUSTODY_TRANSFERRED,
                "CUSTODY_TRANSFER",
                transfer.getTransferId(),
                null, transfer.getTrackingCode(),
                transfer.getFromActorId(), transfer.getHubId(),
                null, null, null,
                transfer.getTransferredAt(),
                payload);
    }

    /**
     * Creates a {@link LogisticTrustEvent} for a Proof-of-Location verification.
     *
     * @param actorId   the actor whose location is being proven
     * @param tenantId  the tenant identifier
     * @param gpsLat    the verified GPS latitude
     * @param gpsLng    the verified GPS longitude
     * @param polHash   the SHA-256 hash of the PoL payload from the mobile app
     */
    public static LogisticTrustEvent forPolVerification(
            final String actorId,
            final String tenantId,
            final double gpsLat,
            final double gpsLng,
            final String polHash) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(polHash, "polHash must not be null");
        final String payload = String.format(
                "{\"actorId\":\"%s\",\"gpsLat\":%f,\"gpsLng\":%f,\"polHash\":\"%s\"}",
                actorId, gpsLat, gpsLng, polHash);
        return new LogisticTrustEvent(
                UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.PROOF_OF_LOCATION_VERIFIED,
                "POL_EVENT", UUID.randomUUID().toString(),
                null, null, actorId, null,
                gpsLat, gpsLng, polHash,
                LocalDateTime.now(), payload);
    }

    /**
     * Creates a {@link LogisticTrustEvent} for a geofence zone crossing.
     *
     * @param actorId   the deliverer who crossed the zone boundary
     * @param tenantId  the tenant identifier
     * @param zoneId    the geofence zone identifier
     * @param zoneName  the geofence zone's display name
     * @param zoneType  the zone classification (e.g. RELAY_HUB, DANGER_ZONE)
     * @param direction {@code ENTER} or {@code EXIT}
     * @param gpsLat    latitude at crossing time
     * @param gpsLng    longitude at crossing time
     * @param missionId the mission in progress at crossing time, if any
     */
    public static LogisticTrustEvent forGeofenceCrossing(
            final String actorId,
            final String tenantId,
            final String zoneId,
            final String zoneName,
            final String zoneType,
            final String direction,
            final double gpsLat,
            final double gpsLng,
            final String missionId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        final String payload = String.format(
                "{\"actorId\":\"%s\",\"zoneId\":\"%s\",\"zoneName\":\"%s\",\"zoneType\":\"%s\"," +
                "\"direction\":\"%s\",\"gpsLat\":%f,\"gpsLng\":%f,\"missionId\":\"%s\"}",
                actorId, zoneId, zoneName != null ? zoneName : "", zoneType != null ? zoneType : "",
                direction, gpsLat, gpsLng, missionId != null ? missionId : "");
        return new LogisticTrustEvent(
                UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.GEOFENCE_CROSSING_RECORDED,
                "GEOFENCE_EVENT", UUID.randomUUID().toString(),
                missionId, null, actorId, null,
                gpsLat, gpsLng, null,
                LocalDateTime.now(), payload);
    }

    /**
     * Creates a {@link LogisticTrustEvent} for DID issuance.
     *
     * @param actorId     the actor receiving the DID
     * @param didDocument the issued DID document
     */
    public static LogisticTrustEvent forDIDIssuance(
            final String actorId,
            final DIDDocument didDocument) {
        Objects.requireNonNull(didDocument);
        final String payload = String.format(
                "{\"actorId\":\"%s\",\"did\":\"%s\",\"publicKey\":\"%s\",\"expiresAt\":\"%s\"}",
                actorId, didDocument.getDid(),
                didDocument.getPublicKeyPem().replaceAll("\n", "\\\\n"),
                didDocument.getExpiresAt());
        return new LogisticTrustEvent(
                UUID.randomUUID().toString(), didDocument.getTenantId(),
                LogisticTrustEventType.DELIVERER_DID_ISSUED,
                "DID_DOCUMENT", didDocument.getDid(),
                null, null, actorId, null,
                null, null, null,
                LocalDateTime.now(), payload);
    }

    /**
     * Creates a {@link LogisticTrustEvent} for FreelancerOrg DID issuance.
     *
     * @param orgId       the FreelancerOrg receiving the DID
     * @param tenantId    the tenant identifier
     * @param tradeName   the FreelancerOrg's commercial trade name
     * @param didDocument the issued DID document
     */
    public static LogisticTrustEvent forFreelancerOrgDIDIssuance(
            final String orgId,
            final String tenantId,
            final String tradeName,
            final DIDDocument didDocument) {
        Objects.requireNonNull(didDocument);
        final String payload = String.format(
                "{\"orgId\":\"%s\",\"tradeName\":\"%s\",\"did\":\"%s\",\"expiresAt\":\"%s\"}",
                orgId, tradeName, didDocument.getDid(), didDocument.getExpiresAt());
        return new LogisticTrustEvent(
                UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.FREELANCER_ORG_DID_ISSUED,
                "DID_DOCUMENT", didDocument.getDid(),
                null, null, orgId, null,
                null, null, null,
                LocalDateTime.now(), payload);
    }

    /**
     * Creates a {@link LogisticTrustEvent} for DID revocation.
     *
     * @param did      the DID string being revoked
     * @param actorId  the actor whose DID is revoked
     * @param tenantId the tenant identifier
     */
    public static LogisticTrustEvent forDIDRevocation(
            final String did,
            final String actorId,
            final String tenantId) {
        final String payload = String.format(
                "{\"actorId\":\"%s\",\"did\":\"%s\",\"revokedAt\":\"%s\"}",
                actorId, did, LocalDateTime.now());
        return new LogisticTrustEvent(
                UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.DELIVERER_DID_REVOKED,
                "DID_DOCUMENT", did,
                null, null, actorId, null,
                null, null, null,
                LocalDateTime.now(), payload);
    }

    /**
     * Creates a {@link LogisticTrustEvent} for a badge being awarded.
     *
     * @param actorId   the actor receiving the badge
     * @param tenantId  the tenant identifier
     * @param badgeType the badge type identifier (e.g., "100_DELIVERIES")
     * @param points    the reputation points associated with the badge
     */
    public static LogisticTrustEvent forBadgeAwarded(
            final String actorId,
            final String tenantId,
            final String badgeType,
            final int points) {
        final String badgeId = UUID.randomUUID().toString();
        final String payload = String.format(
                "{\"actorId\":\"%s\",\"badgeType\":\"%s\",\"points\":%d,\"awardedAt\":\"%s\"}",
                actorId, badgeType, points, LocalDateTime.now());
        return new LogisticTrustEvent(
                UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.BADGE_AWARDED,
                "BADGE", badgeId,
                null, null, actorId, null,
                null, null, null,
                LocalDateTime.now(), payload);
    }

    /**
     * Creates a {@link LogisticTrustEvent} for a DAO zone rule activation.
     *
     * @param zoneId   the DAO zone identifier
     * @param tenantId the tenant identifier
     * @param rule     the JSON-encoded rule definition
     */
    public static LogisticTrustEvent forDaoRuleActivated(
            final String zoneId,
            final String tenantId,
            final String rule) {
        final String ruleId = UUID.randomUUID().toString();
        final String payload = String.format(
                "{\"zoneId\":\"%s\",\"ruleId\":\"%s\",\"rule\":%s,\"activatedAt\":\"%s\"}",
                zoneId, ruleId, rule, LocalDateTime.now());
        return new LogisticTrustEvent(
                UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.DAO_RULE_ACTIVATED,
                "DAO_RULE", ruleId,
                null, null, null, null,
                null, null, null,
                LocalDateTime.now(), payload);
    }

    /**
     * Creates a {@link LogisticTrustEvent} for a mission created on-chain.
     *
     * @param missionId    the mission identifier
     * @param actorId      the assigned deliverer actor
     * @param tenantId     the tenant identifier
     * @param packageCount the number of packages in the mission
     */
    public static LogisticTrustEvent forMissionCreated(
            final String missionId,
            final String actorId,
            final String tenantId,
            final int packageCount) {
        Objects.requireNonNull(missionId, "missionId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        final String payload = String.format(
                "{\"missionId\":\"%s\",\"actorId\":\"%s\",\"packageCount\":%d,\"createdAt\":\"%s\"}",
                missionId, actorId != null ? actorId : "", packageCount, LocalDateTime.now());
        return new LogisticTrustEvent(
                UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.MISSION_CREATED_ON_CHAIN,
                "MISSION", missionId,
                missionId, null, actorId, null,
                null, null, null,
                LocalDateTime.now(), payload);
    }

    /**
     * Creates a {@link LogisticTrustEvent} for a mission completed on-chain.
     *
     * @param missionId the mission identifier
     * @param actorId   the deliverer who completed the mission
     * @param tenantId  the tenant identifier
     */
    public static LogisticTrustEvent forMissionCompleted(
            final String missionId,
            final String actorId,
            final String tenantId) {
        Objects.requireNonNull(missionId, "missionId must not be null");
        final String payload = String.format(
                "{\"missionId\":\"%s\",\"actorId\":\"%s\",\"completedAt\":\"%s\"}",
                missionId, actorId != null ? actorId : "", LocalDateTime.now());
        return new LogisticTrustEvent(
                UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.MISSION_COMPLETED_ON_CHAIN,
                "MISSION", missionId,
                missionId, null, actorId, null,
                null, null, null,
                LocalDateTime.now(), payload);
    }

    /**
     * Creates a {@link LogisticTrustEvent} for a mission cancelled on-chain.
     *
     * @param missionId      the mission identifier
     * @param tenantId       the tenant identifier
     * @param cancelReason   the reason for cancellation
     */
    public static LogisticTrustEvent forMissionCancelled(
            final String missionId,
            final String tenantId,
            final String cancelReason) {
        Objects.requireNonNull(missionId, "missionId must not be null");
        final String payload = String.format(
                "{\"missionId\":\"%s\",\"reason\":\"%s\",\"cancelledAt\":\"%s\"}",
                missionId, cancelReason != null ? cancelReason : "", LocalDateTime.now());
        return new LogisticTrustEvent(
                UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.MISSION_CANCELLED_ON_CHAIN,
                "MISSION", missionId,
                missionId, null, null, null,
                null, null, null,
                LocalDateTime.now(), payload);
    }

    /**
     * Creates a {@link LogisticTrustEvent} for a billing policy activation.
     *
     * @param agencyId  the agency identifier
     * @param policyId  the billing policy identifier
     * @param tenantId  the tenant identifier
     */
    public static LogisticTrustEvent forBillingPolicyActivated(
            final String agencyId,
            final String policyId,
            final String tenantId) {
        final String payload = String.format(
                "{\"agencyId\":\"%s\",\"policyId\":\"%s\",\"activatedAt\":\"%s\"}",
                agencyId, policyId, LocalDateTime.now());
        return new LogisticTrustEvent(
                UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.BILLING_POLICY_ACTIVATED,
                "BILLING_POLICY", policyId,
                null, null, null, null,
                null, null, null,
                LocalDateTime.now(), payload);
    }

    /**
     * Creates a {@link LogisticTrustEvent} for a committed wallet payment.
     *
     * @param paymentIntentId the payment intent identifier
     * @param walletId        the wallet identifier that was debited/credited
     * @param actorId         the wallet owner's actor id
     * @param tenantId        the tenant identifier
     * @param channel         the payment channel (e.g., MTN_MOMO, ORANGE_MONEY, STRIPE)
     * @param externalRef     the provider's financial transaction id
     * @param amount          the committed amount (plain string, e.g. "1000.00")
     * @param currency        the ISO currency code (e.g., "XAF")
     */
    public static LogisticTrustEvent forPaymentCommitted(
            final String paymentIntentId,
            final String walletId,
            final String actorId,
            final String tenantId,
            final String channel,
            final String externalRef,
            final String amount,
            final String currency) {
        final String payload = String.format(
                "{\"walletId\":\"%s\",\"channel\":\"%s\",\"externalRef\":\"%s\","
                        + "\"amount\":\"%s\",\"currency\":\"%s\"}",
                walletId, channel, externalRef, amount, currency);
        return new LogisticTrustEvent(
                UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.PAYMENT_COMMITTED,
                "PAYMENT", paymentIntentId,
                null, null, actorId, null,
                null, null, null,
                LocalDateTime.now(), payload);
    }

    /**
     * Creates a {@link LogisticTrustEvent} for a dispute evidence anchor.
     *
     * @param disputeId    the dispute identifier
     * @param evidenceId   the evidence identifier
     * @param fileKey      the MinIO object key of the evidence file
     * @param tenantId     the tenant identifier
     * @param evidenceHash SHA-256 hash of the evidence content, nullable — anchored
     *                     alongside the fileKey so {@code verifyProof} can later compare
     *                     an independently recomputed hash against it
     */
    public static LogisticTrustEvent forDisputeEvidenceAnchored(
            final String disputeId,
            final String evidenceId,
            final String fileKey,
            final String tenantId,
            final String evidenceHash) {
        final String payload = String.format(
                "{\"disputeId\":\"%s\",\"fileKey\":\"%s\",\"evidenceHash\":\"%s\"}",
                disputeId, fileKey, evidenceHash != null ? evidenceHash : "");
        return new LogisticTrustEvent(
                UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.DISPUTE_EVIDENCE_ANCHORED,
                "DISPUTE_EVIDENCE", evidenceId,
                null, null, null, null,
                null, null, null,
                LocalDateTime.now(), payload);
    }

    // ── Incident Blockchain Factory Methods (v1.1 — IBlockchainAuditPort) ────

    /**
     * Creates a {@link LogisticTrustEvent} representing the creation of an incident.
     * This event is the genesis block of the incident's dedicated blockchain chain.
     *
     * @param incidentId the incident identifier
     * @param chainId    the incident chain identifier (format: INC-{uuid})
     * @param tenantId   the tenant identifier
     * @param payload    the JSON payload describing the incident creation context
     */
    public static LogisticTrustEvent forIncidentCreated(
            final String incidentId,
            final String chainId,
            final String tenantId,
            final String payload) {
        return new LogisticTrustEvent(
                java.util.UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.INCIDENT_CREATED,
                "INCIDENT", incidentId,
                null, null, null, null,
                null, null, null,
                LocalDateTime.now(),
                String.format("{\"incidentId\":\"%s\",\"chainId\":\"%s\",\"data\":%s}",
                        incidentId, chainId, payload != null ? payload : "{}"));
    }

    /**
     * Creates a {@link LogisticTrustEvent} for incident chain initialization.
     * Used when a multi-parcel incident creates its own dedicated chain.
     *
     * @param chainId  the incident chain identifier (format: INC-{uuid})
     * @param tenantId the tenant identifier
     */
    public static LogisticTrustEvent forIncidentChainInitialized(
            final String chainId,
            final String tenantId) {
        return new LogisticTrustEvent(
                java.util.UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.INCIDENT_CHAIN_INITIALIZED,
                "INCIDENT_CHAIN", chainId,
                null, null, null, null,
                null, null, null,
                LocalDateTime.now(),
                String.format("{\"chainId\":\"%s\",\"initializedAt\":\"%s\"}",
                        chainId, LocalDateTime.now()));
    }

    /**
     * Creates a {@link LogisticTrustEvent} for evidence attached to an incident.
     *
     * @param incidentId the incident identifier
     * @param chainId    the incident chain identifier
     * @param tenantId   the tenant identifier
     * @param payload    the evidence payload (type, mediaHash, etc.)
     */
    public static LogisticTrustEvent forEvidenceAttached(
            final String incidentId,
            final String chainId,
            final String tenantId,
            final String payload) {
        return new LogisticTrustEvent(
                java.util.UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.EVIDENCE_ATTACHED,
                "INCIDENT_EVIDENCE", incidentId,
                null, null, null, null,
                null, null, null,
                LocalDateTime.now(),
                String.format("{\"incidentId\":\"%s\",\"chainId\":\"%s\",\"evidence\":%s}",
                        incidentId, chainId, payload != null ? payload : "{}"));
    }

    /**
     * Creates a {@link LogisticTrustEvent} for parcel handover completion.
     * Anchors the double-confirmation handover between original and replacement deliverer.
     *
     * @param incidentId the incident identifier
     * @param chainId    the incident chain identifier
     * @param tenantId   the tenant identifier
     * @param payload    the handover payload (originalDriver, replacementDriver, parcelIds, timestamps)
     */
    public static LogisticTrustEvent forParcelHandoverCompleted(
            final String incidentId,
            final String chainId,
            final String tenantId,
            final String payload) {
        return new LogisticTrustEvent(
                java.util.UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.PARCEL_HANDOVER_COMPLETED,
                "INCIDENT_HANDOVER", incidentId,
                null, null, null, null,
                null, null, null,
                LocalDateTime.now(),
                String.format("{\"incidentId\":\"%s\",\"chainId\":\"%s\",\"handover\":%s}",
                        incidentId, chainId, payload != null ? payload : "{}"));
    }

    /**
     * Creates a {@link LogisticTrustEvent} for inter-agency cooperation completion.
     *
     * @param incidentId the incident identifier
     * @param chainId    the incident chain identifier
     * @param tenantId   the tenant identifier
     * @param payload    the cooperation payload (agencyIds, cooperationType, parcelIds)
     */
    public static LogisticTrustEvent forInterAgencyCooperationCompleted(
            final String incidentId,
            final String chainId,
            final String tenantId,
            final String payload) {
        return new LogisticTrustEvent(
                java.util.UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.INTER_AGENCY_COOPERATION_COMPLETED,
                "INCIDENT_COOPERATION", incidentId,
                null, null, null, null,
                null, null, null,
                LocalDateTime.now(),
                String.format("{\"incidentId\":\"%s\",\"chainId\":\"%s\",\"cooperation\":%s}",
                        incidentId, chainId, payload != null ? payload : "{}"));
    }

    /**
     * Creates a {@link LogisticTrustEvent} for incident closure.
     * This is the terminal block of the incident's dedicated chain.
     *
     * @param incidentId    the incident identifier
     * @param chainId       the incident chain identifier
     * @param tenantId      the tenant identifier
     * @param closureReason the reason for closure
     */
    public static LogisticTrustEvent forIncidentClosed(
            final String incidentId,
            final String chainId,
            final String tenantId,
            final String closureReason) {
        return new LogisticTrustEvent(
                java.util.UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.INCIDENT_CLOSED,
                "INCIDENT", incidentId,
                null, null, null, null,
                null, null, null,
                LocalDateTime.now(),
                String.format("{\"incidentId\":\"%s\",\"chainId\":\"%s\","
                                + "\"reason\":\"%s\",\"closedAt\":\"%s\"}",
                        incidentId, chainId,
                        closureReason != null ? closureReason : "",
                        LocalDateTime.now()));
    }

    /**
     * Creates a {@link LogisticTrustEvent} for parcel chain resumption.
     * Anchors the reconnection of a parcel chain to its normal flow
     * after the linked incident chain is closed.
     *
     * @param parcelId              the parcel identifier
     * @param incidentId            the resolved incident identifier
     * @param tenantId              the tenant identifier
     * @param incidentChainTailHash the last hash of the incident chain (resume proof)
     */
    public static LogisticTrustEvent forParcelChainResumed(
            final String parcelId,
            final String incidentId,
            final String tenantId,
            final String incidentChainTailHash) {
        return new LogisticTrustEvent(
                java.util.UUID.randomUUID().toString(), tenantId,
                LogisticTrustEventType.PARCEL_CHAIN_RESUMED,
                "PARCEL_CHAIN", parcelId,
                null, parcelId, null, null,
                null, null, null,
                LocalDateTime.now(),
                String.format("{\"parcelId\":\"%s\",\"resolvedIncidentId\":\"%s\","
                                + "\"incidentChainTailHash\":\"%s\",\"resumedAt\":\"%s\"}",
                        parcelId, incidentId,
                        incidentChainTailHash != null ? incidentChainTailHash : "GENESIS",
                        LocalDateTime.now()));
    }

    // ── Payload builders ──────────────────────────────────────────────────────

    private static String buildDeliveryProofPayload(final DeliveryProofRecord proof) {
        return String.format(
                "{\"proofId\":\"%s\",\"missionId\":\"%s\",\"packageId\":\"%s\"," +
                "\"actorId\":\"%s\",\"photoHash\":\"%s\",\"gpsLat\":%f,\"gpsLng\":%f," +
                "\"confirmedAt\":\"%s\"}",
                proof.getProofId(), proof.getMissionId(), proof.getPackageId(),
                proof.getActorId(), proof.getPhotoHash(),
                proof.getGpsLat(), proof.getGpsLng(), proof.getConfirmedAt());
    }

    private static String buildCustodyPayload(final CustodyTransferRecord transfer) {
        return String.format(
                "{\"transferId\":\"%s\",\"packageId\":\"%s\",\"trackingCode\":\"%s\"," +
                "\"fromActorId\":\"%s\",\"toActorId\":\"%s\",\"transferType\":\"%s\"," +
                "\"hubId\":\"%s\",\"gpsLat\":\"%s\",\"gpsLng\":\"%s\"," +
                "\"pocHash\":\"%s\",\"transferredAt\":\"%s\"}",
                transfer.getTransferId(), transfer.getPackageId(), transfer.getTrackingCode(),
                transfer.getFromActorId() != null ? transfer.getFromActorId() : "",
                transfer.getToActorId(),
                transfer.getTransferType() != null ? transfer.getTransferType().name() : "",
                transfer.getHubId() != null ? transfer.getHubId() : "",
                transfer.getGpsLat() != null ? transfer.getGpsLat() : "",
                transfer.getGpsLng() != null ? transfer.getGpsLng() : "",
                transfer.getPocHash() != null ? transfer.getPocHash() : "",
                transfer.getTransferredAt());
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getCorrelationId() { return correlationId; }
    public String getTenantId() { return tenantId; }
    public LogisticTrustEventType getLogisticEventType() { return logisticEventType; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getMissionId() { return missionId; }
    public String getPackageTrackingCode() { return packageTrackingCode; }
    public String getActorId() { return actorId; }
    public String getHubId() { return hubId; }
    public Double getGpsLat() { return gpsLat; }
    public Double getGpsLng() { return gpsLng; }
    public String getPolHash() { return polHash; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getAdditionalData() { return additionalData; }

    /**
     * Maps this logistic event type to the corresponding Kernel
     * {@code TrustEventType} string for the Kafka message.
     */
    public String toKernelEventType() {
        return switch (logisticEventType) {
            case DELIVERY_PROOF_RECORDED -> "DELIVERY_PROOF_RECORDED";
            case PACKAGE_CUSTODY_TRANSFERRED -> "PACKAGE_CUSTODY_TRANSFERRED";
            case HUB_DEPOSIT_CONFIRMED -> "HUB_DEPOSIT_CONFIRMED";
            case HUB_PICKUP_CONFIRMED -> "HUB_DEPOSIT_CONFIRMED";
            case DELIVERER_DID_ISSUED -> "DELIVERER_DID_ISSUED";
            case DELIVERER_DID_REVOKED -> "IDENTITY_VERIFIED";
            case BADGE_AWARDED -> "BADGE_AWARDED";
            case PROOF_OF_LOCATION_VERIFIED -> "LOCATION_PROOF_VERIFIED";
            case GEOFENCE_CROSSING_RECORDED -> "GEOFENCE_CROSSING_RECORDED";
            case FREELANCER_ORG_DID_ISSUED -> "FREELANCER_ORG_DID_ISSUED";
            case DAO_RULE_ACTIVATED -> "DAO_RULE_ACTIVATED";
            case BILLING_POLICY_ACTIVATED -> "BILLING_POLICY_ACTIVATED";
            // Incident blockchain event type mappings (v1.1)
            case INCIDENT_CREATED -> "INCIDENT_CREATED";
            case INCIDENT_CHAIN_INITIALIZED -> "INCIDENT_CHAIN_INITIALIZED";
            case EVIDENCE_ATTACHED -> "EVIDENCE_ATTACHED";
            case PARCEL_HANDOVER_COMPLETED -> "PARCEL_HANDOVER_COMPLETED";
            case INTER_AGENCY_COOPERATION_COMPLETED -> "INTER_AGENCY_COOPERATION_COMPLETED";
            case INCIDENT_CLOSED -> "INCIDENT_CLOSED";
            case PARCEL_CHAIN_RESUMED -> "PARCEL_CHAIN_RESUMED";
            default -> "SOLUTION_SPECIFIC_EVENT";
        };
    }

    /**
     * Returns the JSON payload that will be embedded in the Kafka message
     * sent to the {@code yow.trust.events} topic.
     */
    public String toKafkaPayload() {
        return additionalData != null ? additionalData : "{}";
    }

    @Override
    public String toString() {
        return "LogisticTrustEvent{correlationId='" + correlationId
                + "', eventType=" + logisticEventType
                + ", entityId='" + entityId + "', tenantId='" + tenantId + "'}";
    }
}
