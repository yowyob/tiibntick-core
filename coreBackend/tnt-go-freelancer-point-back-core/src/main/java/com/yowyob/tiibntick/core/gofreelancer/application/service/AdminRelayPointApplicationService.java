package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.actor.domain.model.KycStatus;
import com.yowyob.tiibntick.core.actor.application.command.ValidateKycCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.IValidateKycUseCase;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.RelayPointDetailsResponse;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase;
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
import java.util.UUID;

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

    @Override
    public Mono<Void> validateRelayPoint(UUID id, boolean approved, String reason, String loginUrl) {
        KycStatus newStatus = approved ? KycStatus.VERIFIED : KycStatus.REJECTED;
        RelayPointStatus rpStatus = approved ? RelayPointStatus.APPROVED : RelayPointStatus.REJECTED;
        ValidateKycCommand cmd = new ValidateKycCommand(
                DEFAULT_TENANT, id, ActorType.RELAY_OPERATOR, newStatus, "admin", reason);

        return resolveRelayPoint(id)
                .flatMap(rp -> updateLocalStatus(rp, rpStatus, approved)
                        .flatMap(saved -> (approved ? ensureHubProvisioned(saved) : Mono.just(saved)))
                        .flatMap(saved -> validateKycUseCase.validateKyc(cmd)
                                .onErrorResume(e -> {
                                    log.error("KYC validate failed for relay point {} — local status already {}", id, rpStatus, e);
                                    return Mono.empty();
                                })
                                .then(notifyStatusChange(saved, approved
                                        ? "Compte Point Relais approuvé"
                                        : "Compte Point Relais rejeté",
                                        approved
                                                ? "Votre point relais a été approuvé."
                                                : "Votre point relais a été rejeté" + (reason != null ? " : " + reason : ""),
                                        loginUrl,
                                        approved ? NotifyKind.APPROVED : NotifyKind.REJECTED,
                                        reason))))
                .switchIfEmpty(Mono.defer(() ->
                        // Fallback: actor-id only (legacy admin flows)
                        validateKycUseCase.validateKyc(cmd)
                                .then(Mono.fromRunnable(() -> {
                                    if (approved) emailPort.sendAccountApproved(id.toString(), loginUrl);
                                    else emailPort.sendAccountRejected(id.toString(), reason, loginUrl);
                                }))));
    }

    @Override
    public Mono<Void> suspendRelayPoint(UUID id, String loginUrl) {
        log.info("Suspending RelayPoint {}", id);
        return resolveRelayPoint(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpRelayPoint not found: " + id)))
                .flatMap(rp -> updateLocalStatus(rp, RelayPointStatus.SUSPENDED, false)
                        .flatMap(saved -> syncOperatorStatus(saved.getCoreRelayPointId(), OperatorAction.SUSPEND)
                                .then(notifyStatusChange(saved,
                                        "Point Relais suspendu",
                                        "Votre point relais a été suspendu.",
                                        loginUrl, NotifyKind.SUSPENDED, null))));
    }

    @Override
    public Mono<Void> revokeRelayPoint(UUID id, String loginUrl) {
        log.info("Revoking RelayPoint {}", id);
        return resolveRelayPoint(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpRelayPoint not found: " + id)))
                .flatMap(rp -> updateLocalStatus(rp, RelayPointStatus.REVOKED, false)
                        .flatMap(saved -> syncOperatorStatus(saved.getCoreRelayPointId(), OperatorAction.REVOKE)
                                .then(notifyStatusChange(saved,
                                        "Point Relais révoqué",
                                        "Votre point relais a été révoqué.",
                                        loginUrl, NotifyKind.REVOKED, null))));
    }

    @Override
    public Mono<Void> activateRelayPoint(UUID id, String loginUrl) {
        log.info("Activating RelayPoint {}", id);
        return resolveRelayPoint(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpRelayPoint not found: " + id)))
                .flatMap(rp -> updateLocalStatus(rp, RelayPointStatus.APPROVED, true)
                        .flatMap(saved -> syncOperatorStatus(saved.getCoreRelayPointId(), OperatorAction.ACTIVATE)
                                .then(notifyStatusChange(saved,
                                        "Point Relais activé",
                                        "Votre point relais est à nouveau actif.",
                                        loginUrl, NotifyKind.APPROVED, null))));
    }

    // ── helpers ────────────────────────────────────────────────────────────

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
                    int capacity = rp.getMaxDeposits() != null && rp.getMaxDeposits() > 0
                            ? rp.getMaxDeposits() : 10;
                    String operatorActorId = rp.getCoreFreelancerId() != null
                            ? rp.getCoreFreelancerId().toString() : null;

                    return relayHubPort.provisionHub(DEFAULT_TENANT, point, rp.getName(), cityCode,
                                    capacity, operatorActorId)
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

    private Mono<Void> notifyStatusChange(GofpRelayPoint rp, String title, String message,
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

        Mono<Void> pushMono = Mono.empty();
        if (rp.getCoreFreelancerId() != null) {
            pushMono = pushNotificationPort.sendPushNotification(rp.getCoreFreelancerId(), title, message)
                    .doOnError(e -> log.error("Push notify failed for relay point {}", rp.getId(), e))
                    .onErrorResume(e -> Mono.empty());
        }
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
