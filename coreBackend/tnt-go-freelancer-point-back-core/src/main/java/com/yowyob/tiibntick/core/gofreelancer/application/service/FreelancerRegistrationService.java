package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerRegistrationRequest;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerRegistrationResponse;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.FreelancerCreatedEvent;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.ValidationException;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.FreelancerRegistrationUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.FileStoragePort;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.KycVerificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service for freelancer and relay-point registration.
 *
 * <p><strong>KYC gate:</strong> Before persisting any data, the service
 * verifies the submitted identity document (CNI recto by default, falling
 * back to NIU photo) via {@link KycVerificationPort}. If verification fails
 * the registration is rejected with a {@link ValidationException} and nothing
 * is stored.
 *
 * <p>Flow:
 * <ol>
 *   <li>Validate request fields ({@link FreelancerRegistrationValidator})</li>
 *   <li>Identify the primary identity document (cniRecto → nuiPhoto)</li>
 *   <li>Call {@code POST /api/v1/kyc/verify} via {@link KycVerificationPort}</li>
 *   <li>If accepted: store files, publish Kafka event, return response</li>
 *   <li>If rejected: throw {@link ValidationException} — no data is persisted</li>
 * </ol>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FreelancerRegistrationService implements FreelancerRegistrationUseCase {

    private final FreelancerRegistrationValidator validator;
    private final KycVerificationPort kycVerificationPort;
    private final FileStoragePort fileStoragePort;
    private final KafkaEventPublisher eventPublisher;

    @Override
    public Mono<FreelancerRegistrationResponse> register(
            FreelancerRegistrationRequest request,
            FilePart photoCard,
            FilePart cniRecto,
            FilePart cniVerso,
            FilePart nuiPhoto,
            FilePart frontPhoto,
            FilePart backPhoto,
            FilePart storefrontPhoto) {

        return validator.validate(request)
                .flatMap(validRequest -> {
                    // Select the primary identity document to verify:
                    // CNI recto is preferred; fall back to NIU photo for businesses.
                    FilePart primaryDoc = cniRecto != null ? cniRecto : nuiPhoto;

                    if (primaryDoc == null) {
                        return Mono.error(new ValidationException(
                                "At least one identity document is required for KYC verification " +
                                "(cniRecto or nuiPhoto)."));
                    }

                    log.info("Starting KYC verification for registrant email={}", request.getEmail());

                    return kycVerificationPort.verifyDocument(primaryDoc, null)
                            .flatMap(verified -> {
                                if (Boolean.FALSE.equals(verified)) {
                                    log.warn("KYC verification failed for registrant email={}",
                                            request.getEmail());
                                    return Mono.error(new ValidationException(
                                            "Identity document verification failed. " +
                                            "Please provide a valid, legible document."));
                                }

                                log.info("KYC verification passed for registrant email={}",
                                        request.getEmail());

                                // Store files and build the registration record
                                return storeDocuments(request, photoCard, cniRecto, cniVerso,
                                        nuiPhoto, frontPhoto, backPhoto, storefrontPhoto)
                                        .flatMap(docs -> persistAndNotify(request, docs));
                            });
                });
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Saves all uploaded files and returns a simple holder with the resulting
     * storage URLs (using the existing {@link FileStoragePort}).
     */
    private Mono<StoredDocs> storeDocuments(
            FreelancerRegistrationRequest request,
            FilePart photoCard,
            FilePart cniRecto,
            FilePart cniVerso,
            FilePart nuiPhoto,
            FilePart frontPhoto,
            FilePart backPhoto,
            FilePart storefrontPhoto) {

        Mono<String> cniRectoUrl  = saveOptional(cniRecto,       "cni_recto");
        Mono<String> cniVersoUrl  = saveOptional(cniVerso,       "cni_verso");
        Mono<String> nuiUrl       = saveOptional(nuiPhoto,       "nui");
        Mono<String> photoUrl     = saveOptional(photoCard,      "identite");
        Mono<String> frontUrl     = saveOptional(frontPhoto,     "vehicule_avant");
        Mono<String> backUrl      = saveOptional(backPhoto,      "vehicule_arriere");
        Mono<String> storefrontUrl= saveOptional(storefrontPhoto,"devanture");

        return Mono.zip(cniRectoUrl, cniVersoUrl, nuiUrl, photoUrl, frontUrl, backUrl, storefrontUrl)
                .map(t -> new StoredDocs(t.getT1(), t.getT2(), t.getT3(),
                        t.getT4(), t.getT5(), t.getT6(), t.getT7()));
    }

    private Mono<String> saveOptional(FilePart file, String prefix) {
        if (file == null) return Mono.just("");
        return fileStoragePort.saveFilePart(file, prefix).onErrorReturn("");
    }

    /**
     * Assigns an ID, publishes the Kafka {@code FreelancerCreatedEvent}
     * and returns the registration response.
     */
    private Mono<FreelancerRegistrationResponse> persistAndNotify(
            FreelancerRegistrationRequest request,
            StoredDocs docs) {

        UUID freelancerId = UUID.randomUUID();

        FreelancerCreatedEvent event = new FreelancerCreatedEvent(
                freelancerId, request.getEmail());

        try {
            eventPublisher.publishFreelancerCreated(event);
        } catch (Exception ex) {
            log.error("Failed to publish FreelancerCreatedEvent for id={}: {}",
                    freelancerId, ex.getMessage());
            // Non-blocking: registration still succeeds even if Kafka is down
        }

        log.info("Freelancer registered successfully id={} email={}",
                freelancerId, request.getEmail());

        return Mono.just(new FreelancerRegistrationResponse(
                freelancerId, FreelancerStatus.PENDING.name()));
    }

    // -----------------------------------------------------------------------
    // Inner record — only used inside this service
    // -----------------------------------------------------------------------

    private record StoredDocs(
            String cniRectoUrl,
            String cniVersoUrl,
            String nuiPhotoUrl,
            String photoCardUrl,
            String frontPhotoUrl,
            String backPhotoUrl,
            String storefrontPhotoUrl) {}
}
