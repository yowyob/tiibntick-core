package com.yowyob.tiibntick.core.trust.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.adapter.in.web.dto.*;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyVerificationResult;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.ParcelCustodyChain;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.ParcelDigitalTwin;
import com.yowyob.tiibntick.core.trust.application.port.in.*;
import com.yowyob.tiibntick.core.trust.application.service.LogisticProofResolverService;

/**
 * REST Controller — {@code TrustApiController}.
 *
 * <p>Exposes the {@code tnt-trust} use cases as a REST API consumed by:
 * <ul>
 *   <li>{@code tnt-link} — Fil d'Ariane tracking view for clients and deliverers</li>
 *   <li>{@code tnt-agency} — Mission audit trail for agency operators</li>
 *   <li>{@code tnt-point} — Relay hub custody chain display</li>
 *   <li>{@code tnt-go} — Deliverer delivery proof submission</li>
 * </ul>
 *
 * <h3>Base Path</h3>
 * {@code /tnt/trust}
 *
 * <h3>Security</h3>
 * <p>All endpoints are protected by JWT authentication via {@code tnt-auth-core}.
 * The current user identity is injected via {@code @CurrentUser TntUserIdentity}.
 * Permission enforcement is declarative via {@code @RequirePermission} from
 * {@code tnt-roles-core} — no manual permission checks in controller code.
 *
 * <h3>Multi-tenancy</h3>
 * <p>The {@code tenantId} is always resolved from the JWT security context via
 * {@code TntUserIdentity.tenantId()} — for both read and write operations.
 * Every endpoint is authenticated (no anonymous access), so there is no
 * client-supplied {@code tenantId} anywhere in this controller: it is never
 * accepted as a query parameter, and any {@code tenantId} present in a
 * request body is ignored in favor of the authenticated caller's tenant.
 *
 * @author MANFOUO Braun
 * @version 1.1
 */
@RestController
@RequestMapping("/tnt/trust")
@Tag(name = "TiiBnTick Trust API",
        description = "Blockchain proof management for TiiBnTick logistics — "
                + "Fil d'Ariane, DID, PoL, Badges, DAO Rules")
@SecurityRequirement(name = "bearerAuth")
public class TrustApiController {

    private static final Logger log = LoggerFactory.getLogger(TrustApiController.class);

    private final RecordDeliveryProofUseCase recordDeliveryProof;
    private final RecordCustodyTransferUseCase recordCustodyTransfer;
    private final IssueDIDUseCase issueDID;
    private final RecordPolVerificationUseCase recordPolVerification;
    private final GetDeliveryAuditTrailUseCase getDeliveryAuditTrail;
    private final GetActorDIDUseCase getActorDID;
    private final GetCustodyChainUseCase getCustodyChainUseCase;
    private final GetGeofenceCrossingsUseCase getGeofenceCrossingsUseCase;
    private final GetDaoRulesUseCase getDaoRulesUseCase;
    private final GetPolVerificationsUseCase getPolVerificationsUseCase;
    private final LogisticProofResolverService proofResolver;

    public TrustApiController(
            final RecordDeliveryProofUseCase recordDeliveryProof,
            final RecordCustodyTransferUseCase recordCustodyTransfer,
            final IssueDIDUseCase issueDID,
            final RecordPolVerificationUseCase recordPolVerification,
            final GetDeliveryAuditTrailUseCase getDeliveryAuditTrail,
            final GetActorDIDUseCase getActorDID,
            final GetCustodyChainUseCase getCustodyChainUseCase,
            final GetGeofenceCrossingsUseCase getGeofenceCrossingsUseCase,
            final GetDaoRulesUseCase getDaoRulesUseCase,
            final GetPolVerificationsUseCase getPolVerificationsUseCase,
            final LogisticProofResolverService proofResolver) {
        this.recordDeliveryProof = recordDeliveryProof;
        this.recordCustodyTransfer = recordCustodyTransfer;
        this.issueDID = issueDID;
        this.recordPolVerification = recordPolVerification;
        this.getDeliveryAuditTrail = getDeliveryAuditTrail;
        this.getActorDID = getActorDID;
        this.getCustodyChainUseCase = getCustodyChainUseCase;
        this.getGeofenceCrossingsUseCase = getGeofenceCrossingsUseCase;
        this.getDaoRulesUseCase = getDaoRulesUseCase;
        this.getPolVerificationsUseCase = getPolVerificationsUseCase;
        this.proofResolver = proofResolver;
    }

    // ── Delivery Audit Trail (Fil d'Ariane) ───────────────────────────────────

    /**
     * Returns the complete "Fil d'Ariane" for a delivery mission.
     * Lists all delivery proofs ordered chronologically.
     *
     * <p>Consumed by: tnt-link (tracking view), tnt-agency (audit).
     * Requires permission: {@code trust:read}.
     */
    @GetMapping(value = "/delivery/{missionId}/trail", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get delivery audit trail (Fil d'Ariane) for a mission")
    @RequirePermission(resource = "trust", action = "read")
    public Flux<AuditTrailResponse> getDeliveryAuditTrail(
            @PathVariable @Parameter(description = "Delivery mission ID") final String missionId,
            @CurrentUser final TntUserIdentity currentUser) {
        final String tenantId = currentUser.tenantId().toString();
        log.debug("GET /tnt/trust/delivery/{}/trail — tenantId={}", missionId, tenantId);
        return getDeliveryAuditTrail.getByMissionId(missionId, tenantId)
                .map(AuditTrailResponse::fromDeliveryProof);
    }

    /**
     * Returns the complete chain of custody for a package (Fil d'Ariane).
     * Lists all custody transfers ordered chronologically.
     *
     * <p>Consumed by: tnt-link (package tracking), tnt-point (hub display).
     * Requires permission: {@code trust:read}.
     */
    @GetMapping(value = "/package/{trackingCode}/custody", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get package chain of custody (Fil d'Ariane)")
    @RequirePermission(resource = "trust", action = "read")
    public Flux<AuditTrailResponse> getPackageCustodyChain(
            @PathVariable @Parameter(description = "Package tracking code") final String trackingCode,
            @CurrentUser final TntUserIdentity currentUser) {
        final String tenantId = currentUser.tenantId().toString();
        log.debug("GET /tnt/trust/package/{}/custody — tenantId={}", trackingCode, tenantId);
        return getDeliveryAuditTrail.getByPackageTrackingCode(trackingCode, tenantId)
                .map(AuditTrailResponse::fromCustodyTransfer);
    }

    // ── Proof Verification ────────────────────────────────────────────────────

    /**
     * Verifies a blockchain proof on the Hyperledger Fabric ledger.
     * Used to confirm that a proof shown in the UI is genuine.
     *
     * <p>Requires permission: {@code trust:verify}.
     */
    @GetMapping(value = "/verify", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Verify a blockchain proof on-chain")
    @RequirePermission(resource = "trust", action = "verify")
    public Mono<ProofVerificationResponse> verifyProof(
            @RequestParam @Parameter(description = "Fabric transaction hash") final String txHash,
            @RequestParam @Parameter(description = "Expected SHA-256 data hash") final String expectedHash) {
        return proofResolver.verifyProofOnChain(txHash, expectedHash)
                .map(valid -> valid
                        ? ProofVerificationResponse.valid(txHash, expectedHash)
                        : ProofVerificationResponse.invalid(txHash, expectedHash));
    }

    // ── Actor Identity (DID) ──────────────────────────────────────────────────

    /**
     * Retrieves the DID document for a deliverer actor.
     * Consumed by platform modules to display and verify actor identity.
     *
     * <p>Requires permission: {@code trust:read}.
     */
    @GetMapping(value = "/actors/{actorId}/did", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get DID document for an actor")
    @RequirePermission(resource = "trust", action = "read")
    public Mono<DIDDocumentResponse> getActorDID(
            @PathVariable @Parameter(description = "Actor ID") final String actorId,
            @CurrentUser final TntUserIdentity currentUser) {
        return getActorDID.getByActorId(actorId, currentUser.tenantId().toString())
                .map(DIDDocumentResponse::from);
    }

    /**
     * Verifies whether an actor holds a specific badge type.
     * Returns HTTP 200 with {@code {"valid": true/false}}.
     *
     * <p>Requires permission: {@code trust:verify}.
     */
    @GetMapping(value = "/actors/{actorId}/badges/{badgeType}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Verify whether an actor holds a specific badge")
    @RequirePermission(resource = "trust", action = "verify")
    public Mono<BadgeVerificationResult> verifyBadge(
            @PathVariable @Parameter(description = "Actor ID") final String actorId,
            @PathVariable @Parameter(description = "Badge type (e.g., 100_DELIVERIES)") final String badgeType,
            @CurrentUser final TntUserIdentity currentUser) {
        return getActorDID.verifyBadge(actorId, badgeType, currentUser.tenantId().toString())
                .map(valid -> new BadgeVerificationResult(actorId, badgeType, valid));
    }

    // ── Write Operations ──────────────────────────────────────────────────────

    /**
     * Records a delivery proof on the blockchain.
     * Called by the deliverer's mobile app via tnt-go after completing a delivery.
     *
     * <p>Requires permission: {@code trust:anchor}.
     * The {@code tenantId} is resolved from the JWT context of the authenticated deliverer.
     */
    @PostMapping(value = "/delivery/proof",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Record a delivery proof on the blockchain")
    @RequirePermission(resource = "trust", action = "anchor")
    public Mono<CorrelationIdResponse> recordDeliveryProof(
            @Valid @RequestBody final DeliveryProofRequest request,
            @CurrentUser final TntUserIdentity currentUser) {
        log.info("POST /tnt/trust/delivery/proof — proofId={}, actor={}",
                request.proofId(), currentUser.actorId());
        return recordDeliveryProof.record(request.toDomain(currentUser.tenantId().toString()))
                .map(correlationId -> new CorrelationIdResponse(correlationId,
                        "Delivery proof submitted for blockchain anchoring."));
    }

    /**
     * Records a package custody transfer on the blockchain.
     * Called when a package changes hands between actors.
     *
     * <p>Requires permission: {@code trust:anchor}.
     */
    @PostMapping(value = "/custody/transfer",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Record a package custody transfer on the blockchain")
    @RequirePermission(resource = "trust", action = "anchor")
    public Mono<CorrelationIdResponse> recordCustodyTransfer(
            @Valid @RequestBody final CustodyTransferRequest request,
            @CurrentUser final TntUserIdentity currentUser) {
        log.info("POST /tnt/trust/custody/transfer — transferId={}, actor={}",
                request.transferId(), currentUser.actorId());
        return recordCustodyTransfer.record(request.toDomain(currentUser.tenantId().toString()))
                .map(correlationId -> new CorrelationIdResponse(correlationId,
                        "Custody transfer submitted for blockchain anchoring."));
    }

    /**
     * Issues a DID for a deliverer actor.
     * Called by tnt-actor-core when a new deliverer is registered and verified.
     *
     * <p>Requires permission: {@code trust:anchor}.
     */
    @PostMapping(value = "/actors/did/issue",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Issue a Decentralized Identifier (DID) for a deliverer actor")
    @RequirePermission(resource = "trust", action = "anchor")
    public Mono<DIDDocumentResponse> issueDID(
            @Valid @RequestBody final DIDIssueRequest request,
            @CurrentUser final TntUserIdentity currentUser) {
        log.info("POST /tnt/trust/actors/did/issue — actorId={}, requester={}",
                request.actorId(), currentUser.userId());
        return issueDID.issue(request.actorId(), currentUser.tenantId().toString(), request.publicKeyPem())
                .map(DIDDocumentResponse::from);
    }

    /**
     * Records a Proof-of-Location verification on the blockchain.
     * Called by tnt-realtime-core after validating a PoL from the mobile app.
     *
     * <p>Requires permission: {@code trust:anchor}.
     */
    @PostMapping(value = "/pol/record",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Record a verified Proof-of-Location on the blockchain")
    @RequirePermission(resource = "trust", action = "anchor")
    public Mono<CorrelationIdResponse> recordPolVerification(
            @Valid @RequestBody final PolVerificationRequest request,
            @CurrentUser final TntUserIdentity currentUser) {
        log.info("POST /tnt/trust/pol/record — actorId={}", request.actorId());
        return recordPolVerification.record(
                request.actorId(), request.gpsLat(), request.gpsLng(),
                request.polHash(), currentUser.tenantId().toString())
                .map(correlationId -> new CorrelationIdResponse(correlationId,
                        "Proof-of-Location submitted for blockchain anchoring."));
    }

    // ── Response records ──────────────────────────────────────────────────────

    /** Simple correlation ID response for accepted write operations. */
    public record CorrelationIdResponse(String correlationId, String message) {}

    /** Badge verification result. */
    public record BadgeVerificationResult(String actorId, String badgeType, boolean valid) {}
    // ── : FreelancerOrg DID endpoint ─────────────────────────────────────

    /**
     * POST /api/trust/did/freelancer-org
     * Issues a DID for a FreelancerOrganization.
     */
    @org.springframework.web.bind.annotation.PostMapping("/did/freelancer-org")
    @RequirePermission(resource = "trust", action = "anchor")
    public reactor.core.publisher.Mono<com.yowyob.tiibntick.core.trust.adapter.in.web.dto.DIDDocumentResponse>
            issueFreelancerOrgDID(
                    @Valid @org.springframework.web.bind.annotation.RequestBody FreelancerOrgDIDRequest request,
                    @CurrentUser final TntUserIdentity currentUser) {
        return issueDID.issueForFreelancerOrg(
                        request.orgId(), currentUser.tenantId().toString(), request.tradeName(), request.publicKeyPem())
                .map(com.yowyob.tiibntick.core.trust.adapter.in.web.dto.DIDDocumentResponse::from);
    }

    public record FreelancerOrgDIDRequest(
            @NotBlank(message = "orgId is required") String orgId,
            String tenantId,
            @NotBlank(message = "tradeName is required") String tradeName,
            @NotBlank(message = "publicKeyPem is required") String publicKeyPem
    ) {}

    // ── Chain of Custody endpoints ────────────────────────────────────────────

    /**
     * Returns the complete Chain of Custody for a parcel.
     * Answers: "Who held this parcel, in what order, and is the chain intact?"
     */
    @GetMapping(value = "/package/{packageId}/custody-chain", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Chain of Custody for a parcel")
    @RequirePermission(resource = "trust", action = "read")
    public Mono<ParcelCustodyChain> getCustodyChain(
            @PathVariable @Parameter(description = "Package UUID") final String packageId,
            @CurrentUser final TntUserIdentity currentUser) {
        final String tenantId = currentUser.tenantId().toString();
        log.debug("GET /tnt/trust/package/{}/custody-chain — tenantId={}", packageId, tenantId);
        return getCustodyChainUseCase.getByPackageId(packageId, tenantId);
    }

    /**
     * Verifies the cryptographic integrity of a parcel's custody chain.
     */
    @GetMapping(value = "/package/{packageId}/custody-chain/verify", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Verify custody chain cryptographic integrity")
    @RequirePermission(resource = "trust", action = "verify")
    public Mono<CustodyVerificationResult> verifyCustodyChain(
            @PathVariable @Parameter(description = "Package UUID") final String packageId,
            @CurrentUser final TntUserIdentity currentUser) {
        final String tenantId = currentUser.tenantId().toString();
        log.debug("GET /tnt/trust/package/{}/custody-chain/verify — tenantId={}", packageId, tenantId);
        return getCustodyChainUseCase.verifyCustodyChain(packageId, tenantId);
    }

    /**
     * Returns the actor UUID currently holding the parcel.
     */
    @GetMapping(value = "/package/{packageId}/current-custodian", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get current custodian of a parcel")
    @RequirePermission(resource = "trust", action = "read")
    public Mono<String> getCurrentCustodian(
            @PathVariable @Parameter(description = "Package UUID") final String packageId,
            @CurrentUser final TntUserIdentity currentUser) {
        final String tenantId = currentUser.tenantId().toString();
        log.debug("GET /tnt/trust/package/{}/current-custodian — tenantId={}", packageId, tenantId);
        return getCustodyChainUseCase.getCurrentCustodian(packageId, tenantId);
    }

    /**
     * Returns the Digital Twin of a parcel (composite: lifecycle state + custody chain + blockchain status).
     */
    @GetMapping(value = "/package/{packageId}/digital-twin", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Parcel Digital Twin (composite view: state + custody + blockchain)")
    @RequirePermission(resource = "trust", action = "read")
    public Mono<ParcelDigitalTwin> getDigitalTwin(
            @PathVariable @Parameter(description = "Package UUID") final String packageId,
            @CurrentUser final TntUserIdentity currentUser) {
        final String tenantId = currentUser.tenantId().toString();
        log.debug("GET /tnt/trust/package/{}/digital-twin — tenantId={}", packageId, tenantId);
        return proofResolver.resolveDigitalTwin(packageId, tenantId);
    }

    // ── Geofencing / DAO Rules / Proof-of-Location (read path) ───────────────

    /**
     * Returns the geofence zone crossing history for an actor.
     *
     * <p>Requires permission: {@code trust:read}.
     */
    @GetMapping(value = "/geofence/{actorId}/crossings", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get geofence zone crossing history for an actor")
    @RequirePermission(resource = "trust", action = "read")
    public Flux<GeofenceCrossingResponse> getGeofenceCrossings(
            @PathVariable @Parameter(description = "Actor ID") final String actorId,
            @CurrentUser final TntUserIdentity currentUser) {
        final String tenantId = currentUser.tenantId().toString();
        log.debug("GET /tnt/trust/geofence/{}/crossings — tenantId={}", actorId, tenantId);
        return getGeofenceCrossingsUseCase.getByActorId(actorId, tenantId)
                .map(GeofenceCrossingResponse::from);
    }

    /**
     * Returns the DAO zone governance rule activation history for a zone.
     *
     * <p>Requires permission: {@code trust:read}.
     */
    @GetMapping(value = "/dao/{zoneId}/rules", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get DAO governance rule activation history for a zone")
    @RequirePermission(resource = "trust", action = "read")
    public Flux<DaoRuleResponse> getDaoRules(
            @PathVariable @Parameter(description = "DAO zone ID") final String zoneId,
            @CurrentUser final TntUserIdentity currentUser) {
        final String tenantId = currentUser.tenantId().toString();
        log.debug("GET /tnt/trust/dao/{}/rules — tenantId={}", zoneId, tenantId);
        return getDaoRulesUseCase.getByZoneId(zoneId, tenantId)
                .map(DaoRuleResponse::from);
    }

    /**
     * Returns the Proof-of-Location verification history for an actor.
     *
     * <p>Requires permission: {@code trust:read}.
     */
    @GetMapping(value = "/pol/{actorId}/verifications", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Proof-of-Location verification history for an actor")
    @RequirePermission(resource = "trust", action = "read")
    public Flux<PolVerificationResponse> getPolVerifications(
            @PathVariable @Parameter(description = "Actor ID") final String actorId,
            @CurrentUser final TntUserIdentity currentUser) {
        final String tenantId = currentUser.tenantId().toString();
        log.debug("GET /tnt/trust/pol/{}/verifications — tenantId={}", actorId, tenantId);
        return getPolVerificationsUseCase.getByActorId(actorId, tenantId)
                .map(PolVerificationResponse::from);
    }
}