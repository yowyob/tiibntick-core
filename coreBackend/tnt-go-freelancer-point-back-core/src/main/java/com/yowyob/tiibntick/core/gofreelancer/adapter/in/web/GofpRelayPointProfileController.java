package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RelayPointUpdateRequest;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.RelayExpectedParcelView;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.RelayPointPublicView;
import com.yowyob.tiibntick.core.gofreelancer.application.service.RelayPointAccessGuard;
import com.yowyob.tiibntick.core.gofreelancer.application.service.RelayPointDirectoryService;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.GofpRelayPointUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for GofpRelayPoint profile management.
 *
 * Base path: /api/v1/gofp/relay-point-profiles
 *
 * <p>Access (see {@link RelayPointAccessGuard}): a relay point is managed by its owner (the caller's
 * freelancer profile) or by staff; approval and deletion are staff-only; a relay point created by its
 * owner starts pending. Senders read approved relay points through a public view that carries no
 * owner identity or contact.
 *
 * @author François-Charles ATANGA
 */
@RestController
@RequestMapping("/api/v1/gofp/relay-point-profiles")
@RequiredArgsConstructor
public class GofpRelayPointProfileController {

    private final GofpRelayPointUseCase relayPointUseCase;
    private final RelayPointAccessGuard guard;
    private final RelayPointDirectoryService directory;

    @PostMapping
    public Mono<ResponseEntity<GofpRelayPoint>> createOrUpdate(
            @RequestBody GofpRelayPoint relayPoint,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.prepareWrite(relayPoint, ctx)
                .flatMap(relayPointUseCase::createOrUpdate)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    // ── The caller's own relay point ──────────────────────────────────────

    /** The caller's relay point (most recent when several), 404 when the caller has none. */
    @GetMapping("/mine")
    public Mono<ResponseEntity<GofpRelayPoint>> getMine(@CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.myRelayPoints(ctx).next()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /** Creates (pending) or updates the caller's own relay point; the owner is always the caller. */
    @PutMapping("/mine")
    public Mono<ResponseEntity<GofpRelayPoint>> upsertMine(
            @RequestBody GofpRelayPoint relayPoint,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.prepareMine(relayPoint, ctx)
                .flatMap(relayPointUseCase::createOrUpdate)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    /** Parcels the caller's relay point should receive and has not received yet. */
    @GetMapping("/mine/expected-parcels")
    public Flux<RelayExpectedParcelView> getMyExpectedParcels(@CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.myRelayPoints(ctx).next().flatMapMany(directory::expectedParcels);
    }

    // ── Reads ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public Mono<ResponseEntity<GofpRelayPoint>> getById(
            @PathVariable UUID id,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.requireOwnerOrStaff(id, ctx)
                .flatMap(rp -> relayPointUseCase.findById(rp.getId()))
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/by-core-relay-point/{coreRelayPointId}")
    public Mono<ResponseEntity<GofpRelayPoint>> getByCoreRelayPointId(
            @PathVariable UUID coreRelayPointId,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.requireOwnerOrStaff(coreRelayPointId, ctx)
                .flatMap(rp -> relayPointUseCase.findByCoreRelayPointId(rp.getCoreRelayPointId()))
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/by-freelancer/{coreFreelancerId}")
    public Flux<GofpRelayPoint> getByFreelancer(
            @PathVariable UUID coreFreelancerId,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        if (RelayPointAccessGuard.isStaff(ctx)) {
            return relayPointUseCase.findByFreelancer(coreFreelancerId);
        }
        return guard.myRelayPoints(ctx)
                .filter(rp -> coreFreelancerId.equals(rp.getCoreFreelancerId()))
                .flatMap(rp -> relayPointUseCase.findById(rp.getId()));
    }

    /** Every relay point, whatever its status: staff only. */
    @GetMapping
    public Flux<GofpRelayPoint> getAll(@CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.requireStaff(ctx).thenMany(Flux.defer(relayPointUseCase::findAll));
    }

    /** Approved and active relay points, as a sender sees them (no owner identity or contact). */
    @GetMapping("/active-approved")
    public Flux<RelayPointPublicView> getActiveApproved() {
        return directory.publicViews(relayPointUseCase.findActiveApproved());
    }

    /** Relay points by status (e.g. pending ones awaiting approval): staff only. */
    @GetMapping("/by-status/{status}")
    public Flux<GofpRelayPoint> getByStatus(
            @PathVariable RelayPointStatus status,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.requireStaff(ctx).thenMany(Flux.defer(() -> relayPointUseCase.findByStatus(status)));
    }

    // ── Writes ────────────────────────────────────────────────────────────

    /** Approval, rejection, suspension: staff only. */
    @PatchMapping("/{id}/status")
    public Mono<ResponseEntity<GofpRelayPoint>> updateStatus(
            @PathVariable UUID id,
            @RequestParam RelayPointStatus status,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.requireStaff(ctx)
                .then(Mono.defer(() -> relayPointUseCase.updateStatus(id, status)))
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    /** The owner may open or pause their relay point; it is listed to senders only once approved. */
    @PatchMapping("/{id}/active")
    public Mono<ResponseEntity<GofpRelayPoint>> setActive(
            @PathVariable UUID id,
            @RequestParam Boolean active,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.requireOwnerOrStaff(id, ctx)
                .flatMap(rp -> relayPointUseCase.setActive(rp.getId(), active))
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    /**
     * Re-syncs denormalised owner contact fields (phone, email, name)
     * from GofpFreelancer/GofpUser. Call after owner profile update.
     */
    @PostMapping("/by-core-relay-point/{coreRelayPointId}/sync-owner-contact")
    public Mono<ResponseEntity<GofpRelayPoint>> syncOwnerContact(
            @PathVariable UUID coreRelayPointId,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.requireOwnerOrStaff(coreRelayPointId, ctx)
                .flatMap(rp -> relayPointUseCase.syncOwnerContact(rp.getCoreRelayPointId()))
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    /** Deletion: staff only. */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(
            @PathVariable UUID id,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.requireStaff(ctx)
                .then(Mono.defer(() -> relayPointUseCase.delete(id)))
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    /**
     * Updates the storage space dimensions for a relay point (its owner or staff).
     *
     * <p>Only the dimension fields of {@link RelayPointUpdateRequest} are applied here.
     * The relay point is fetched first so no other fields are overwritten.
     *
     * <p>Example request body:
     * <pre>{"storageLength": 10, "storageWidth": 5, "storageHeight": 3, "storageDimensionUnit": "m"}</pre>
     */
    @PatchMapping("/{id}/storage")
    public Mono<ResponseEntity<GofpRelayPoint>> updateStorage(
            @PathVariable UUID id,
            @RequestBody RelayPointUpdateRequest request,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.requireOwnerOrStaff(id, ctx)
                .flatMap(owned -> relayPointUseCase.findById(owned.getId()))
                .flatMap(rp -> {
                    if (request.getStorageLength() != null)        rp.setStorageLength(request.getStorageLength());
                    if (request.getStorageWidth() != null)         rp.setStorageWidth(request.getStorageWidth());
                    if (request.getStorageHeight() != null)        rp.setStorageHeight(request.getStorageHeight());
                    if (request.getStorageDimensionUnit() != null) rp.setStorageDimensionUnit(request.getStorageDimensionUnit());
                    return relayPointUseCase.createOrUpdate(rp);
                })
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    /**
     * Returns active/approved relay points that have enough remaining storage
     * capacity to accept a packet of the given volume, as a sender sees them.
     *
     * @param packetVolumeM3 volume of the parcel in m³ (e.g. 0.027 for a 30x30x30 cm box)
     */
    @GetMapping("/available")
    public Flux<RelayPointPublicView> getAvailableForPacket(
            @RequestParam double packetVolumeM3) {
        return directory.publicViews(relayPointUseCase.findWithSufficientCapacity(packetVolumeM3));
    }
}
