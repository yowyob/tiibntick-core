package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.application.service.GofpFileUploadService;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for file uploads.
 *
 * <p>All endpoints consume {@code multipart/form-data} with a single {@code file} part.
 * Files are stored locally and the URL is immediately persisted in the relevant entity.
 *
 * <p>Download: files are served statically at {@code GET /uploads/{filename}}.
 *
 * @author François-Charles ATANGA
 */
@RestController
@RequestMapping("/api/v1/gofp/files")
@RequiredArgsConstructor
public class GofpFileUploadController {

    private final GofpFileUploadService uploadService;

    // ── User profile photo ────────────────────────────────────────────────────

    /**
     * Upload or replace the profile photo of a user.
     * POST /api/v1/gofp/files/users/{coreUserId}/profile-photo
     */
    @PostMapping(value = "/users/{coreUserId}/profile-photo",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<GofpUser>> uploadProfilePhoto(
            @PathVariable UUID coreUserId,
            @RequestPart("file") FilePart file) {
        return uploadService.uploadProfilePhoto(coreUserId, file)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    // ── Freelancer onboarding docs ────────────────────────────────────────────

    /**
     * POST /api/v1/gofp/files/freelancers/{freelancerId}/cni-recto
     */
    @PostMapping(value = "/freelancers/{freelancerId}/cni-recto",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<FreelancerOnboardingDocs>> uploadCniRecto(
            @PathVariable UUID freelancerId,
            @RequestPart("file") FilePart file) {
        return uploadService.uploadCniRecto(freelancerId, file)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    /**
     * POST /api/v1/gofp/files/freelancers/{freelancerId}/cni-verso
     */
    @PostMapping(value = "/freelancers/{freelancerId}/cni-verso",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<FreelancerOnboardingDocs>> uploadCniVerso(
            @PathVariable UUID freelancerId,
            @RequestPart("file") FilePart file) {
        return uploadService.uploadCniVerso(freelancerId, file)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    /**
     * POST /api/v1/gofp/files/freelancers/{freelancerId}/nui-photo
     */
    @PostMapping(value = "/freelancers/{freelancerId}/nui-photo",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<FreelancerOnboardingDocs>> uploadNuiPhoto(
            @PathVariable UUID freelancerId,
            @RequestPart("file") FilePart file) {
        return uploadService.uploadNuiPhoto(freelancerId, file)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    /**
     * POST /api/v1/gofp/files/freelancers/{freelancerId}/photo-card
     */
    @PostMapping(value = "/freelancers/{freelancerId}/photo-card",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<FreelancerOnboardingDocs>> uploadPhotoCard(
            @PathVariable UUID freelancerId,
            @RequestPart("file") FilePart file) {
        return uploadService.uploadPhotoCard(freelancerId, file)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    /**
     * POST /api/v1/gofp/files/freelancers/{freelancerId}/commercial-register
     */
    @PostMapping(value = "/freelancers/{freelancerId}/commercial-register",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<FreelancerOnboardingDocs>> uploadCommercialRegister(
            @PathVariable UUID freelancerId,
            @RequestPart("file") FilePart file) {
        return uploadService.uploadCommercialRegister(freelancerId, file)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    // ── Vehicle photos ────────────────────────────────────────────────────────

    /**
     * POST /api/v1/gofp/files/freelancers/{freelancerId}/vehicle/front-photo
     */
    @PostMapping(value = "/freelancers/{freelancerId}/vehicle/front-photo",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<FreelancerVehicle>> uploadVehicleFrontPhoto(
            @PathVariable UUID freelancerId,
            @RequestPart("file") FilePart file) {
        return uploadService.uploadVehicleFrontPhoto(freelancerId, file)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    /**
     * POST /api/v1/gofp/files/freelancers/{freelancerId}/vehicle/back-photo
     */
    @PostMapping(value = "/freelancers/{freelancerId}/vehicle/back-photo",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<FreelancerVehicle>> uploadVehicleBackPhoto(
            @PathVariable UUID freelancerId,
            @RequestPart("file") FilePart file) {
        return uploadService.uploadVehicleBackPhoto(freelancerId, file)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    // ── Relay point visuals ───────────────────────────────────────────────────

    /**
     * POST /api/v1/gofp/files/relay-points/{coreRelayPointId}/storefront-photo
     */
    @PostMapping(value = "/relay-points/{coreRelayPointId}/storefront-photo",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<RelayPointVisuals>> uploadStorefrontPhoto(
            @PathVariable UUID coreRelayPointId,
            @RequestPart("file") FilePart file) {
        return uploadService.uploadStorefrontPhoto(coreRelayPointId, file)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    /**
     * POST /api/v1/gofp/files/relay-points/{coreRelayPointId}/shop-photo
     */
    @PostMapping(value = "/relay-points/{coreRelayPointId}/shop-photo",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<RelayPointVisuals>> uploadShopPhoto(
            @PathVariable UUID coreRelayPointId,
            @RequestPart("file") FilePart file) {
        return uploadService.uploadShopPhoto(coreRelayPointId, file)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    // ── Packet proof ──────────────────────────────────────────────────────────

    /**
     * POST /api/v1/gofp/files/packets/{corePacketId}/cover-image
     */
    @PostMapping(value = "/packets/{corePacketId}/cover-image",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<PacketProof>> uploadPacketCoverImage(
            @PathVariable UUID corePacketId,
            @RequestPart("file") FilePart file) {
        return uploadService.uploadPacketCoverImage(corePacketId, file)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }
}
