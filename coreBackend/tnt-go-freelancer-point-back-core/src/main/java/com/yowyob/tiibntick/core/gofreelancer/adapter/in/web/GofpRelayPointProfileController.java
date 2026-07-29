package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RelayPointUpdateRequest;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.GofpRelayPointUseCase;
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
 * @author François-Charles ATANGA
 */
@RestController
@RequestMapping("/api/v1/gofp/relay-point-profiles")
@RequiredArgsConstructor
public class GofpRelayPointProfileController {

    private final GofpRelayPointUseCase relayPointUseCase;

    @PostMapping
    public Mono<ResponseEntity<GofpRelayPoint>> createOrUpdate(@RequestBody GofpRelayPoint relayPoint) {
        return relayPointUseCase.createOrUpdate(relayPoint)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<GofpRelayPoint>> getById(@PathVariable UUID id) {
        return relayPointUseCase.findById(id)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/by-core-relay-point/{coreRelayPointId}")
    public Mono<ResponseEntity<GofpRelayPoint>> getByCoreRelayPointId(@PathVariable UUID coreRelayPointId) {
        return relayPointUseCase.findByCoreRelayPointId(coreRelayPointId)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/by-freelancer/{coreFreelancerId}")
    public Flux<GofpRelayPoint> getByFreelancer(@PathVariable UUID coreFreelancerId) {
        return relayPointUseCase.findByFreelancer(coreFreelancerId);
    }

    @GetMapping
    public Flux<GofpRelayPoint> getAll() {
        return relayPointUseCase.findAll();
    }

    @GetMapping("/active-approved")
    public Flux<GofpRelayPoint> getActiveApproved() {
        return relayPointUseCase.findActiveApproved();
    }

    @GetMapping("/by-status/{status}")
    public Flux<GofpRelayPoint> getByStatus(@PathVariable RelayPointStatus status) {
        return relayPointUseCase.findByStatus(status);
    }

    @PatchMapping("/{id}/status")
    public Mono<ResponseEntity<GofpRelayPoint>> updateStatus(
            @PathVariable UUID id,
            @RequestParam RelayPointStatus status) {
        return relayPointUseCase.updateStatus(id, status)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @PatchMapping("/{id}/active")
    public Mono<ResponseEntity<GofpRelayPoint>> setActive(
            @PathVariable UUID id,
            @RequestParam Boolean active) {
        return relayPointUseCase.setActive(id, active)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    /**
     * Re-syncs denormalised owner contact fields (phone, email, name)
     * from GofpFreelancer/GofpUser. Call after owner profile update.
     */
    @PostMapping("/by-core-relay-point/{coreRelayPointId}/sync-owner-contact")
    public Mono<ResponseEntity<GofpRelayPoint>> syncOwnerContact(@PathVariable UUID coreRelayPointId) {
        return relayPointUseCase.syncOwnerContact(coreRelayPointId)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable UUID id) {
        return relayPointUseCase.delete(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    /**
     * Updates the storage space dimensions for a relay point.
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
            @RequestBody RelayPointUpdateRequest request) {
        return relayPointUseCase.findById(id)
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
     * capacity to accept a packet of the given volume.
     *
     * <p>Used by the client app during announcement creation to show only
     * relay points that can physically store the parcel.
     *
     * @param packetVolumeM3 volume of the parcel in m³ (e.g. 0.027 for a 30x30x30 cm box)
     */
    @GetMapping("/available")
    public Flux<GofpRelayPoint> getAvailableForPacket(
            @RequestParam double packetVolumeM3) {
        return relayPointUseCase.findWithSufficientCapacity(packetVolumeM3);
    }
}
