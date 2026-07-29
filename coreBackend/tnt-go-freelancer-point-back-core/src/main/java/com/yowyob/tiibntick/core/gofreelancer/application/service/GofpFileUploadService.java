package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.*;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.FileStoragePort;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerOnboardingDocsRepository;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerVehicleRepository;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.RelayPointVisualsRepository;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.PacketProofRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates file uploads and immediately persists the resulting URL
 * into the relevant entity.
 *
 * <p>Each method follows the same pattern:
 * <ol>
 *   <li>Save the file via {@link FileStoragePort}</li>
 *   <li>Load the entity by its ID</li>
 *   <li>Set the new URL field</li>
 *   <li>Save and return the updated entity</li>
 * </ol>
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GofpFileUploadService {

    private final FileStoragePort              fileStoragePort;
    private final GofpUserRepository           userRepository;
    private final FreelancerOnboardingDocsRepository onboardingDocsRepository;
    private final FreelancerVehicleRepository  vehicleRepository;
    private final RelayPointVisualsRepository  relayPointVisualRepository;
    private final PacketProofRepository        packetProofRepository;

    // ── GofpUser: profile photo ───────────────────────────────────────────────

    public Mono<GofpUser> uploadProfilePhoto(UUID coreUserId, FilePart file) {
        return fileStoragePort.saveFilePart(file, "profile")
                .flatMap(url -> userRepository.findByCoreUserId(coreUserId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + coreUserId)))
                        .flatMap(user -> {
                            user.setProfilePhotoUrl(url);
                            user.setUpdatedAt(Instant.now());
                            log.info("Profile photo updated for coreUserId={}", coreUserId);
                            return userRepository.save(user);
                        }));
    }

    // ── FreelancerOnboardingDocs: CNI & NUI ───────────────────────────────────

    public Mono<FreelancerOnboardingDocs> uploadCniRecto(UUID freelancerId, FilePart file) {
        return fileStoragePort.saveFilePart(file, "cni_recto")
                .flatMap(url -> loadOrCreateDocs(freelancerId)
                        .flatMap(docs -> { docs.setCniRectoUrl(url); return onboardingDocsRepository.save(docs); }));
    }

    public Mono<FreelancerOnboardingDocs> uploadCniVerso(UUID freelancerId, FilePart file) {
        return fileStoragePort.saveFilePart(file, "cni_verso")
                .flatMap(url -> loadOrCreateDocs(freelancerId)
                        .flatMap(docs -> { docs.setCniVersoUrl(url); return onboardingDocsRepository.save(docs); }));
    }

    public Mono<FreelancerOnboardingDocs> uploadNuiPhoto(UUID freelancerId, FilePart file) {
        return fileStoragePort.saveFilePart(file, "nui")
                .flatMap(url -> loadOrCreateDocs(freelancerId)
                        .flatMap(docs -> { docs.setNuiPhotoUrl(url); return onboardingDocsRepository.save(docs); }));
    }

    public Mono<FreelancerOnboardingDocs> uploadPhotoCard(UUID freelancerId, FilePart file) {
        return fileStoragePort.saveFilePart(file, "identite")
                .flatMap(url -> loadOrCreateDocs(freelancerId)
                        .flatMap(docs -> { docs.setPhotoCardUrl(url); return onboardingDocsRepository.save(docs); }));
    }

    public Mono<FreelancerOnboardingDocs> uploadCommercialRegister(UUID freelancerId, FilePart file) {
        return fileStoragePort.saveFilePart(file, "commercial_register")
                .flatMap(url -> loadOrCreateDocs(freelancerId)
                        .flatMap(docs -> { docs.setCommercialRegisterUrl(url); return onboardingDocsRepository.save(docs); }));
    }

    // ── FreelancerVehicle: vehicle photos ─────────────────────────────────────

    public Mono<FreelancerVehicle> uploadVehicleFrontPhoto(UUID freelancerId, FilePart file) {
        return fileStoragePort.saveFilePart(file, "vehicule_avant")
                .flatMap(url -> loadOrCreateVehicle(freelancerId)
                        .flatMap(v -> { v.setFrontPhotoUrl(url); return vehicleRepository.save(v); }));
    }

    public Mono<FreelancerVehicle> uploadVehicleBackPhoto(UUID freelancerId, FilePart file) {
        return fileStoragePort.saveFilePart(file, "vehicule_arriere")
                .flatMap(url -> loadOrCreateVehicle(freelancerId)
                        .flatMap(v -> { v.setBackPhotoUrl(url); return vehicleRepository.save(v); }));
    }

    // ── RelayPointVisuals: storefront & shop photos ───────────────────────────

    public Mono<RelayPointVisuals> uploadStorefrontPhoto(UUID coreRelayPointId, FilePart file) {
        return fileStoragePort.saveFilePart(file, "devanture")
                .flatMap(url -> loadOrCreateVisuals(coreRelayPointId)
                        .flatMap(v -> { v.setStorefrontPhotoUrl(url); return relayPointVisualRepository.save(v); }));
    }

    public Mono<RelayPointVisuals> uploadShopPhoto(UUID coreRelayPointId, FilePart file) {
        return fileStoragePort.saveFilePart(file, "interieur")
                .flatMap(url -> loadOrCreateVisuals(coreRelayPointId)
                        .flatMap(v -> { v.setShopPhotoUrl(url); return relayPointVisualRepository.save(v); }));
    }

    // ── PacketProof: cover image ──────────────────────────────────────────────

    public Mono<PacketProof> uploadPacketCoverImage(UUID corePacketId, FilePart file) {
        return fileStoragePort.saveFilePart(file, "packet")
                .flatMap(url -> loadOrCreatePacketProof(corePacketId)
                        .flatMap(p -> { p.setCoverImageUrl(url); return packetProofRepository.save(p); }));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Mono<FreelancerOnboardingDocs> loadOrCreateDocs(UUID freelancerId) {
        return onboardingDocsRepository.findByFreelancerId(freelancerId)
                .switchIfEmpty(Mono.defer(() -> {
                    FreelancerOnboardingDocs docs = FreelancerOnboardingDocs.builder()
                            .id(UUID.randomUUID())
                            .freelancerId(freelancerId)
                            .build();
                    return onboardingDocsRepository.save(docs);
                }));
    }

    private Mono<FreelancerVehicle> loadOrCreateVehicle(UUID freelancerId) {
        return vehicleRepository.findByFreelancerId(freelancerId)
                .switchIfEmpty(Mono.defer(() -> {
                    FreelancerVehicle v = FreelancerVehicle.builder()
                            .id(UUID.randomUUID())
                            .freelancerId(freelancerId)
                            .build();
                    return vehicleRepository.save(v);
                }));
    }

    private Mono<RelayPointVisuals> loadOrCreateVisuals(UUID coreRelayPointId) {
        return relayPointVisualRepository.findByCoreRelayPointId(coreRelayPointId)
                .switchIfEmpty(Mono.defer(() -> {
                    RelayPointVisuals v = RelayPointVisuals.builder()
                            .id(UUID.randomUUID())
                            .coreRelayPointId(coreRelayPointId)
                            .build();
                    return relayPointVisualRepository.save(v);
                }));
    }

    private Mono<PacketProof> loadOrCreatePacketProof(UUID corePacketId) {
        return packetProofRepository.findByCorePacketId(corePacketId)
                .switchIfEmpty(Mono.defer(() -> {
                    PacketProof p = PacketProof.builder()
                            .id(UUID.randomUUID())
                            .corePacketId(corePacketId)
                            .build();
                    return packetProofRepository.save(p);
                }));
    }
}
