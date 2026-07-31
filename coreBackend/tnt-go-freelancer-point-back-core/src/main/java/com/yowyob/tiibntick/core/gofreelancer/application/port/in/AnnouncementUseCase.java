package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RespondAnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.SubscriptionResponseDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port for announcement management use cases.
 * Orchestrates {@code DeliveryAnnouncementUseCase} / {@code DeliveryQueryUseCase}
 * (tnt-delivery-core) — GOFP does not own a second announcement lifecycle.
 *
 * @author MANFOUO BRAUN
 */
public interface AnnouncementUseCase {

    /** Publishes via delivery-core ({@code publishAnnouncement}). */
    Mono<AnnouncementResponseDTO> createAnnouncement(AnnouncementRequestDTO request);

    Flux<AnnouncementResponseDTO> getAllAnnouncements();

    Mono<AnnouncementResponseDTO> getAnnouncement(UUID id);

    /**
     * Candidate-asymmetric view: other freelancers' proposed prices are redacted.
     */
    Mono<AnnouncementResponseDTO> getAnnouncementForCandidate(UUID id, UUID viewerFreelancerId);

    Flux<AnnouncementResponseDTO> getAnnouncementsByClientId(UUID clientId);

    /** @deprecated Local update path — prefer cancel + republish via delivery-core. */
    @Deprecated
    Mono<AnnouncementResponseDTO> updateAnnouncement(UUID id, AnnouncementRequestDTO request);

    Mono<Void> deleteAnnouncement(UUID id);

    /**
     * Idempotent re-publish hook (announcement already created as published by create).
     * Kept for REST compatibility — returns current delivery-core state.
     */
    Mono<AnnouncementResponseDTO> publishAnnouncement(UUID id);

    /**
     * Freelancer responds / subscribes via delivery-core {@code respondToAnnouncement}.
     * Enforces quota via {@code FreelancerQuotaService}.
     */
    Mono<AnnouncementResponseDTO> respondToAnnouncement(UUID announcementId, RespondAnnouncementRequestDTO request);

    /** @deprecated Prefer {@link #respondToAnnouncement}. */
    @Deprecated
    Mono<Void> initiateSubscription(UUID announcementId, UUID freelancerId);

    Flux<SubscriptionResponseDTO> getSubscriptionsForAnnouncement(UUID announcementId);

    /**
     * Client selects a response via delivery-core {@code selectResponse} (by responseId).
     * Escrow for QUOTE_REQUEST happens here; FIXED_PRICE was escrowed at publish.
     */
    Mono<AnnouncementResponseDTO> assignResponse(UUID announcementId, UUID clientId, UUID responseId);

    /** @deprecated Prefer {@link #assignResponse} with responseId. */
    @Deprecated
    Mono<AnnouncementResponseDTO> assignFreelancer(UUID announcementId, UUID freelancerId);

    Flux<AnnouncementResponseDTO> getSubscriptionsByFreelancerId(UUID freelancerId);
}
