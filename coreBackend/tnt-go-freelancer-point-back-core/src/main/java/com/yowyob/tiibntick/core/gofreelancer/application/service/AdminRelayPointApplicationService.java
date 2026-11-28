package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpNotificationTemplates;
import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.actor.domain.model.KycStatus;
import com.yowyob.tiibntick.core.actor.application.command.ValidateKycCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.IValidateKycUseCase;
import com.yowyob.tiibntick.core.geo.application.port.in.IManageHubApprovalUseCase;
import com.yowyob.tiibntick.core.geo.application.port.in.IRevokeRelayHubUseCase;
import com.yowyob.tiibntick.core.geo.domain.exception.RelayHubStateException;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.RelayPointDetailsResponse;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.RelayPointRevocation;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.EmailPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IRelayHubPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Admin lifecycle for GOFP relay points — persists {@link GofpRelayPoint} status,
 * best-effort syncs {@code RELAY_OPERATOR} actor, notifies via notify-core adapters.
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRelayPointApplicationService implements AdminRelayPointUseCase {

    private final IValidateKycUseCase validateKycUseCase;
    private final EmailPort emailPort;
    private final PushNotificationPort pushNotificationPort;
    private final GofpRelayPointRepository gofpRelayPointRepository;
    private final IRelayOperatorRepository relayOperatorRepository;
    private final FreelancerVehicleApplicationService vehicleApplicationService;
    private final IRelayHubPort relayHubPort;
    private final AddressUseCase addressUseCase;
    private final IRevokeRelayHubUseCase revokeRelayHubUseCase;
    private final GofpRelayRevocationApplier revocationApplier;
    private final IManageHubApprovalUseCase hubApproval;

    /**
     * D-185: capacity of the hub of a relay that declared none (default 10). A platform setting until the
     * administration platform manages it ({@code tnt.gofp.relay.default-parcel-capacity}).
     */
    @org.springframework.beans.factory.annotation.Value("${tnt.gofp.relay.default-parcel-capacity:10}")
    private int defaultParcelCapacity = 10;

    void setDefaultParcelCapacity(int defaultParcelCapacity) {
        this.defaultParcelCapacity = defaultParcelCapacity;
    }

    private static final UUID DEFAULT_TENANT = TenantContextHolder.SYSTEM_TENANT;

    @Override
    public Flux<RelayPointDetailsResponse> getPendingRelayPoints() {
        return gofpRelayPointRepository.findAllByStatus(RelayPointStatus.PENDING)
                .map(this::toDetails);
    }

    @Override
    public Flux<RelayPointDetailsResponse> getAllRelayPoints(RelayPointStatus status) {
        Flux<GofpRelayPoint> source = status == null
                ? gofpRelayPointRepository.findAll()
                : gofpRelayPointRepository.findAllByStatus(status);
        return source.map(this::toDetails);
    }

    @Override
    public Mono<RelayPointDetailsResponse> getRelayPointDetails(UUID id) {
        return resolveRelayPoint(id)
                .map(this::toDetails)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpRelayPoint not found: " + id)));
    }

    /**
     * Approval (PENDING -> APPROVED) or rejection (PENDING -> REJECTED) of a relay point, same machine as the couriers'
     * (D-138): asking for the current status again is a no-op, any other status is refused (409). On approval the
     * core hub is provisioned when missing; with a core hub, the core decides (it is authoritative, D-138) and the
     * relay point copies the result. An unknown relay point falls back to the legacy KYC by actor id.
     */
    @Override
    public Mono<Void> validateRelayPoint(UUID id, boolean approved, String reason, String loginUrl) {
        KycStatus newStatus = approved ? KycStatus.VERIFIED : KycStatus.REJECTED;
        RelayPointStatus rpStatus = approved ? RelayPointStatus.APPROVED : RelayPointStatus.REJECTED;
        ValidateKycCommand cmd = new ValidateKycCommand(
                DEFAULT_TENANT, id, ActorType.RELAY_OPERATOR, newStatus, "admin", reason);

        return resolveRelayPoint(id)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(found -> found.isEmpty()
                        // Fallback: actor-id only (legacy admin flows)
                        ? validateKycUseCase.validateKyc(cmd)
                                .then(Mono.fromRunnable(() -> {
                                    if (approved) emailPort.sendAccountApproved(id.toString(), loginUrl);
                                    else emailPort.sendAccountRejected(id.toString(), reason, loginUrl);
                                }))
                        : requireTransition(found.get(), RelayPointStatus.PENDING, rpStatus)
                                .flatMap(rp -> approved
                                        ? ensureHubProvisioned(rp).flatMap(p -> coreApproval(p,
                                                hubId -> hubApproval.approve(null, hubId, null)))
                                        : coreApproval(rp, hubId -> hubApproval.reject(null, hubId, reason, null)))
                                .flatMap(rp -> updateLocalStatus(rp, rpStatus, approved))
                                .flatMap(saved -> validateKycUseCase.validateKyc(cmd)
                                        .onErrorResume(e -> {
                                            log.error("KYC validate failed for relay point {} — local status already {}", id, rpStatus, e);
                                            return Mono.empty();
                                        })
                                        .then(notifyStatusChange(saved, approved
                                                ? GofpNotificationTemplates.RELAY_STATUS_APPROVED
                                                : (reason != null ? GofpNotificationTemplates.RELAY_STATUS_REJECTED_REASON
                                                        : GofpNotificationTemplates.RELAY_STATUS_REJECTED),
                                                reason != null ? java.util.Map.<String, Object>of("reason", reason)
                                                        : java.util.Map.<String, Object>of(),
                                                loginUrl,
                                                approved ? NotifyKind.APPROVED : NotifyKind.REJECTED,
                                                reason)))
                                .then());
    }

    /**
     * D-138: the approval of the core hub is authoritative and the relay point copies it. With a core hub, the change
     * is made there first; a refusal of the core machine is a 409 for the relay point too. A relay point whose hub is
     * not (yet) known to the core follows its own machine alone.
     */
    private Mono<GofpRelayPoint> coreApproval(GofpRelayPoint rp, Function<UUID, Mono<RelayHub>> change) {
        UUID hubId = rp.getCoreRelayPointId();
        if (hubId == null) return Mono.just(rp);
        return change.apply(hubId)
                // this flow aligns the relay point itself: GofpRelayApprovalApplier must not do it again
                .contextWrite(ctx -> ctx.put(GofpRelayApprovalApplier.FROM_GO_ROUTE, Boolean.TRUE))
                .doOnNext(hub -> log.info("RelayPoint {}: core hub {} approval now {}", rp.getId(), hubId,
                        hub.approvalStatus()))
                .thenReturn(rp)
                .onErrorResume(GeoNotFoundException.class, e -> {
                    log.warn("RelayPoint {}: hub {} unknown to the core, approval kept on the relay point only",
                            rp.getId(), hubId);
                    return Mono.just(rp);
                })
                .onErrorMap(RelayHubStateException.class, e -> new RelayPointStatusException(
                        "Relay point " + rp.getId() + ": the core hub refuses the change (" + e.getMessage() + ")"));
    }

    @Override
    public Mono<Void> suspendRelayPoint(UUID id, String loginUrl) {
        log.info("Suspending RelayPoint {}", id);
        return resolveRelayPoint(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpRelayPoint not found: " + id)))
                .flatMap(rp -> requireTransition(rp, RelayPointStatus.APPROVED, RelayPointStatus.SUSPENDED))
                .flatMap(rp -> coreApproval(rp, hubId -> hubApproval.suspend(null, hubId, null, null)))
                .flatMap(rp -> updateLocalStatus(rp, RelayPointStatus.SUSPENDED, false)
                        .flatMap(saved -> syncOperatorStatus(saved.getCoreRelayPointId(), OperatorAction.SUSPEND)
                                .then(notifyStatusChange(saved,
                                        GofpNotificationTemplates.RELAY_STATUS_SUSPENDED, java.util.Map.of(),
                                        loginUrl, NotifyKind.SUSPENDED, null))));
    }

    /**
     * Definitive revocation (decision D-129). With a core hub, the core revokes it ({@link IRevokeRelayHubUseCase}:
     * status, reason, parcels in custody) and tells its listeners, among which {@link GofpRelayRevocationApplier}
     * aligns this relay point and notifies its operator. Without a core hub (never provisioned), the relay point is
     * revoked locally with the same effects; its parcels in custody are then unknown.
     */
    @Override
    public Mono<RelayPointRevocation> revokeRelayPoint(UUID id, String reason, UUID adminActorId, String loginUrl) {
        if (reason == null || reason.isBlank()) {
            return Mono.error(new IllegalArgumentException("a revocation reason is mandatory"));
        }
        if (adminActorId == null) {
            return Mono.error(new IllegalArgumentException("the administrator is mandatory"));
        }
        String trimmed = reason.strip();
        log.info("Revoking RelayPoint {} by {}", id, adminActorId);
        return resolveRelayPoint(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpRelayPoint not found: " + id)))
                .flatMap(this::requireRevocable)
                .flatMap(rp -> {
                    UUID hubId = rp.getCoreRelayPointId();
                    Mono<RelayPointRevocation> local = Mono.defer(() -> revocationApplier
                            .apply(rp, DEFAULT_TENANT, trimmed, loginUrl)
                            .map(saved -> new RelayPointRevocation(saved.getId(), hubId,
                                    RelayPointStatus.REVOKED.name(), trimmed, Instant.now(), false, false,
                                    java.util.List.of())));
                    if (hubId == null) {
                        return local;
                    }
                    return revokeRelayHubUseCase.revoke(null, hubId, trimmed, adminActorId)
                            .contextWrite(ctx -> loginUrl == null ? ctx
                                    : ctx.put(GofpRelayRevocationApplier.LOGIN_URL_CONTEXT_KEY, loginUrl))
                            .map(r -> new RelayPointRevocation(rp.getId(), hubId, RelayPointStatus.REVOKED.name(),
                                    r.hub().revocationReason(), r.hub().revokedAt(), true, r.custodyKnown(),
                                    r.parcelsInCustody()))
                            .onErrorResume(GeoNotFoundException.class, e -> {
                                log.warn("RelayPoint {}: hub {} unknown to the core, revoked locally", id, hubId);
                                return local;
                            })
                            .onErrorResume(RelayHubStateException.class, e -> relayHubPort.findHub(hubId, null)
                                    .flatMap(hub -> {
                                        if (hub.isRevoked()) {
                                            // the core hub is already revoked (definitive): align this relay point
                                            log.warn("RelayPoint {}: hub {} already revoked in the core, aligned locally",
                                                    id, hubId);
                                            return local;
                                        }
                                        // D-138: the core is authoritative, its refusal stands (409)
                                        return Mono.<RelayPointRevocation>error(new RelayPointStatusException(
                                                "Relay point " + id + ": the core hub is " + hub.approvalStatus()
                                                        + ", it cannot be revoked (D-135)"));
                                    }));
                });
    }

    /**
     * Same machine as the couriers' (decision D-135, {@code DeliveryPersonStatus}): only an APPROVED or SUSPENDED
     * relay point can be revoked; a PENDING one is rejected instead; REJECTED and REVOKED are final.
     */
    private Mono<GofpRelayPoint> requireRevocable(GofpRelayPoint rp) {
        if (rp.getStatus() == RelayPointStatus.REVOKED) return Mono.error(new RelayPointRevokedException(rp.getId()));
        if (rp.getStatus() != RelayPointStatus.APPROVED && rp.getStatus() != RelayPointStatus.SUSPENDED) {
            return Mono.error(new RelayPointStatusException("Relay point " + rp.getId() + " is " + rp.getStatus()
                    + ": only an APPROVED or SUSPENDED relay point can be revoked (a PENDING one is rejected, D-135)"));
        }
        return Mono.just(rp);
    }

    /**
     * Suspension and reactivation follow the couriers' machine ({@code DeliveryPersonStatus}): APPROVED -> SUSPENDED
     * and SUSPENDED -> APPROVED only. Asking for the current status again is a no-op (empty: nothing written, nobody
     * notified); any other status is refused (409) — a PENDING relay point is validated, not activated.
     */
    private Mono<GofpRelayPoint> requireTransition(GofpRelayPoint rp, RelayPointStatus from, RelayPointStatus to) {
        if (rp.getStatus() == RelayPointStatus.REVOKED) return Mono.error(new RelayPointRevokedException(rp.getId()));
        if (rp.getStatus() == to) {
            log.info("RelayPoint {} already {}: nothing to do", rp.getId(), to);
            return Mono.empty();
        }
        if (rp.getStatus() != from) {
            return Mono.error(new RelayPointStatusException("Relay point " + rp.getId() + " is " + rp.getStatus()
                    + ": only a " + from + " relay point can become " + to));
        }
        return Mono.just(rp);
    }

    /** An admin transition refused by the relay point's status (409). */
    public static class RelayPointStatusException extends IllegalStateException {
        public RelayPointStatusException(String message) {
            super(message);
        }
    }

    /** A change requested on a revoked relay point (409). */
    public static class RelayPointRevokedException extends RelayPointStatusException {
        public RelayPointRevokedException(UUID id) {
            super("Relay point " + id + " is revoked: the revocation is definitive");
        }
    }

    @Override
    public Mono<Void> activateRelayPoint(UUID id, String loginUrl) {
        log.info("Activating RelayPoint {}", id);
        return resolveRelayPoint(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpRelayPoint not found: " + id)))
                .flatMap(rp -> requireTransition(rp, RelayPointStatus.SUSPENDED, RelayPointStatus.APPROVED))
                .flatMap(rp -> coreApproval(rp, hubId -> hubApproval.reactivate(null, hubId, null)))
                .flatMap(rp -> updateLocalStatus(rp, RelayPointStatus.APPROVED, true)
                        .flatMap(saved -> syncOperatorStatus(saved.getCoreRelayPointId(), OperatorAction.ACTIVATE)
                                .then(notifyStatusChange(saved,
                                        GofpNotificationTemplates.RELAY_STATUS_REACTIVATED, java.util.Map.of(),
                                        loginUrl, NotifyKind.APPROVED, null))));
    }

    @Override
    public Mono<Void> changeStatus(UUID id, RelayPointStatus target, String reason, String loginUrl) {
        if (target == null) return Mono.error(new IllegalArgumentException("a target status is mandatory"));
        return switch (target) {
            case APPROVED -> resolveRelayPoint(id)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpRelayPoint not found: " + id)))
                    .flatMap(rp -> rp.getStatus() == RelayPointStatus.SUSPENDED
                            ? activateRelayPoint(id, loginUrl)
                            : validateRelayPoint(id, true, null, loginUrl));
            case REJECTED -> resolveRelayPoint(id)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpRelayPoint not found: " + id)))
                    .flatMap(rp -> validateRelayPoint(id, false, reason, loginUrl));
            case SUSPENDED -> suspendRelayPoint(id, loginUrl);
            case PENDING -> Mono.error(new RelayPointStatusException(
                    "Relay point " + id + ": no transition leads back to PENDING"));
            case REVOKED -> Mono.error(new IllegalArgumentException(
                    "a revocation needs a reason: PUT /api/v1/admin/tnt-go-freelancer/relay-points/{id}/revoke"));
        };
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /**
     * The approval path of {@link #validateRelayPoint} for a relay point registered by its owner
     * while manual approval is suspended ({@code tnt.gofp.relay.auto-approve}): APPROVED and active,
     * geo hub provisioned, KYC VERIFIED. No e-mail or push: nothing to announce, and a failed
     * notification must never block a registration. KYC is best-effort, as on approval.
     * <p>Same machine as a staff approval (D-138): the core hub is approved first (it is authoritative and
     * the relay point copies it), so that the relay is a proposable destination at once (D-144).
     */
    public Mono<GofpRelayPoint> approveOnRegistration(GofpRelayPoint rp) {
        ValidateKycCommand cmd = new ValidateKycCommand(DEFAULT_TENANT, rp.getId(), ActorType.RELAY_OPERATOR,
                KycStatus.VERIFIED, "auto-approve", "manual approval suspended until the admin platform exists");
        return requireTransition(rp, RelayPointStatus.PENDING, RelayPointStatus.APPROVED)
                .flatMap(pending -> ensureHubProvisioned(pending).defaultIfEmpty(pending))
                .flatMap(p -> coreApproval(p, hubId -> hubApproval.approve(null, hubId, null)))
                .flatMap(p -> updateLocalStatus(p, RelayPointStatus.APPROVED, true))
                .flatMap(saved -> validateKycUseCase.validateKyc(cmd)
                        .onErrorResume(e -> {
                            log.warn("KYC auto-validation failed for relay point {} — relay point already APPROVED: {}",
                                    saved.getId(), e.getMessage());
                            return Mono.empty();
                        })
                        .thenReturn(saved));
    }

    private Mono<GofpRelayPoint> resolveRelayPoint(UUID id) {
        return gofpRelayPointRepository.findById(id)
                .switchIfEmpty(gofpRelayPointRepository.findByCoreRelayPointId(id));
    }

    private Mono<GofpRelayPoint> updateLocalStatus(GofpRelayPoint rp, RelayPointStatus status, boolean active) {
        rp.setStatus(status);
        rp.setIsActive(active);
        rp.setUpdatedAt(Instant.now());
        return gofpRelayPointRepository.save(rp);
    }

    /**
     * Ensures the relay point has a real {@code tnt-geo-core} {@code RelayHub} backing its
     * {@code coreRelayPointId}, provisioning one (freelance hub, no agency branch — see
     * {@code RelayHub.branchId}) if it doesn't exist yet.
     *
     * <p>{@code coreRelayPointId} is client-supplied and unvalidated at registration time
     * (see {@code GofpRelayPointProfileController}), so on first approval it almost always
     * points at nothing real in geo-core. Best-effort: provisioning requires the relay
     * point's physical address to have GPS coordinates — if it doesn't (landmark-only
     * address), this defers rather than fabricating a location, and never blocks approval.
     */
    private Mono<GofpRelayPoint> ensureHubProvisioned(GofpRelayPoint rp) {
        return relayHubPort.findHub(rp.getCoreRelayPointId(), DEFAULT_TENANT)
                .map(existingHub -> rp)
                .onErrorResume(GeoNotFoundException.class, e -> provisionAndLinkHub(rp))
                .onErrorResume(e -> {
                    log.error("Unexpected error checking geo hub for relay point {} — skipping provisioning",
                            rp.getId(), e);
                    return Mono.just(rp);
                });
    }

    private Mono<GofpRelayPoint> provisionAndLinkHub(GofpRelayPoint rp) {
        UUID addressId = rp.getPhysicalAccessAddressId() != null
                ? rp.getPhysicalAccessAddressId() : rp.getPostalAddressId();
        if (addressId == null) {
            log.warn("RelayPoint {} has no address on file — deferring geo hub provisioning", rp.getId());
            return Mono.just(rp);
        }

        return addressUseCase.getAddressById(addressId)
                .flatMap(dto -> {
                    var address = dto.getAddress();
                    var coordinates = address != null ? address.getCoordinates() : java.util.Optional.<com.yowyob.tiibntick.common.vo.GeoCoordinates>empty();
                    if (coordinates.isEmpty()) {
                        log.warn("RelayPoint {} address {} has no GPS coordinates — deferring geo hub provisioning",
                                rp.getId(), addressId);
                        return Mono.just(rp);
                    }
                    GeoPoint point = GeoPoint.of(
                            coordinates.get().getLatitude(), coordinates.get().getLongitude());
                    String cityCode = address.getCity() != null ? address.getCity() : "UNKNOWN";
                    // D-185: the capacity the relay declared, never the subscription quota; default when undeclared
                    int capacity = rp.getParcelCapacity() != null && rp.getParcelCapacity() > 0
                            ? rp.getParcelCapacity() : defaultParcelCapacity;
                    // H1: the hub's operator is the owner's actor (freelancer's core user), never a profile id.
                    return revocationApplier.operatorActor(DEFAULT_TENANT, rp)
                            .map(UUID::toString)
                            .defaultIfEmpty("")
                            .flatMap(operatorActorId -> relayHubPort.provisionHub(DEFAULT_TENANT, point, rp.getName(),
                                    cityCode, capacity, operatorActorId.isEmpty() ? null : operatorActorId))
                            .flatMap(hub -> {
                                rp.setCoreRelayPointId(hub.id());
                                rp.setUpdatedAt(Instant.now());
                                log.info("Linked RelayPoint {} to newly provisioned geo hub {}", rp.getId(), hub.id());
                                return gofpRelayPointRepository.save(rp);
                            });
                })
                .doOnError(e -> log.error(
                        "Failed to provision geo hub for relay point {} — approval proceeds, "
                                + "will retry on next admin action",
                        rp.getId(), e))
                .onErrorResume(e -> Mono.just(rp));
    }

    private enum OperatorAction { ACTIVATE, SUSPEND, REVOKE }

    /**
     * @param hubId {@code GofpRelayPoint.coreRelayPointId}, i.e. {@code tnt-geo-core}
     *              {@code RelayHub.id} — the same identity space {@code RelayOperatorProfile.hubId}
     *              lives in (see {@code IRelayOperatorRepository.findByHubId}). If the relay point
     *              has never been provisioned a real hub, this legitimately matches nothing.
     */
    private Mono<Void> syncOperatorStatus(UUID hubId, OperatorAction action) {
        if (hubId == null) return Mono.empty();
        return relayOperatorRepository.findByHubId(DEFAULT_TENANT, hubId)
                .flatMap(profile -> {
                    var updated = switch (action) {
                        case ACTIVATE -> profile.activate();
                        case SUSPEND -> profile.suspend();
                        case REVOKE -> profile.deactivate();
                    };
                    return relayOperatorRepository.save(updated);
                })
                .doOnNext(p -> log.info("Synced RELAY_OPERATOR actor={} hub={} action={}",
                        p.actorId(), hubId, action))
                .doOnError(e -> log.error("Failed to sync RELAY_OPERATOR for hub {}", hubId, e))
                .onErrorResume(e -> Mono.empty())
                .then()
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.debug("No RELAY_OPERATOR profile for hub {} — skip actor sync", hubId)));
    }

    private enum NotifyKind { APPROVED, REJECTED, SUSPENDED, REVOKED }

    private Mono<Void> notifyStatusChange(GofpRelayPoint rp, String pushTemplateKey,
                                          java.util.Map<String, Object> pushParams,
                                          String loginUrl, NotifyKind kind, String reason) {
        String email = rp.getOwnerEmail() != null ? rp.getOwnerEmail() : rp.getId().toString();
        Mono<Void> emailMono = Mono.fromRunnable(() -> {
            switch (kind) {
                case APPROVED -> emailPort.sendAccountApproved(email, loginUrl);
                case REJECTED -> emailPort.sendAccountRejected(email, reason, loginUrl);
                case SUSPENDED -> emailPort.sendAccountSuspended(email, loginUrl);
                case REVOKED -> emailPort.sendAccountRevoked(email, loginUrl);
            }
        });

        // H1: the in-app notification goes to the operator's actor (relay operator profile, else the owner
        // freelancer's core user), never to the freelancer profile id.
        Mono<Void> pushMono = revocationApplier.operatorActor(DEFAULT_TENANT, rp)
                .flatMap(actor -> pushNotificationPort.sendLocalized(actor, pushTemplateKey, pushParams))
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("Relay point {}: no operator actor found, in-app notification not sent", rp.getId())))
                .doOnError(e -> log.error("Push notify failed for relay point {}", rp.getId(), e))
                .onErrorResume(e -> Mono.empty())
                .then();
        return Mono.when(emailMono, pushMono);
    }

    private RelayPointDetailsResponse toDetails(GofpRelayPoint rp) {
        Double totalM3 = null;
        if (rp.getStorageLength() != null && rp.getStorageWidth() != null && rp.getStorageHeight() != null) {
            totalM3 = vehicleApplicationService.calculateVolumeInM3(
                    rp.getStorageLength(), rp.getStorageWidth(), rp.getStorageHeight(),
                    rp.getStorageDimensionUnit());
        }
        return RelayPointDetailsResponse.builder()
                .id(rp.getId())
                .freelancerId(rp.getCoreFreelancerId())
                .ownerFirstName(rp.getOwnerFirstName())
                .ownerLastName(rp.getOwnerLastName())
                .ownerEmail(rp.getOwnerEmail())
                .ownerPhone(rp.getOwnerPhone())
                .name(rp.getName())
                .status(rp.getStatus() != null ? rp.getStatus().name() : null)
                .isActive(rp.getIsActive())
                .addressId(rp.getPhysicalAccessAddressId() != null
                        ? rp.getPhysicalAccessAddressId() : rp.getPostalAddressId())
                .rating(rp.getRating())
                .depositsUsed(rp.getDepositsUsed())
                .maxDeposits(rp.getMaxDeposits())
                .storageLength(rp.getStorageLength())
                .storageWidth(rp.getStorageWidth())
                .storageHeight(rp.getStorageHeight())
                .storageDimensionUnit(rp.getStorageDimensionUnit())
                .storageTotalM3(totalM3)
                .createdAt(rp.getCreatedAt() != null ? rp.getCreatedAt().toString() : null)
                .updatedAt(rp.getUpdatedAt() != null ? rp.getUpdatedAt().toString() : null)
                .build();
    }
}
