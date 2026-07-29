package com.yowyob.tiibntick.core.gofreelancer.domain.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.SubscriptionResponseDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port for announcement management use cases.
 */
public interface AnnouncementUseCase {

    Mono<AnnouncementResponseDTO> createAnnouncement(AnnouncementRequestDTO request);

    Flux<AnnouncementResponseDTO> getAllAnnouncements();

    Mono<AnnouncementResponseDTO> getAnnouncement(UUID id);

    Flux<AnnouncementResponseDTO> getAnnouncementsByClientId(UUID clientId);

    Mono<AnnouncementResponseDTO> updateAnnouncement(UUID id, AnnouncementRequestDTO request);

    Mono<Void> deleteAnnouncement(UUID id);

    Mono<AnnouncementResponseDTO> publishAnnouncement(UUID id);

    Mono<Void> initiateSubscription(UUID announcementId, UUID freelancerId);

    Flux<SubscriptionResponseDTO> getSubscriptionsForAnnouncement(UUID announcementId);

    Mono<AnnouncementResponseDTO> assignFreelancer(UUID announcementId, UUID freelancerId);

    Flux<AnnouncementResponseDTO> getSubscriptionsByFreelancerId(UUID freelancerId);
}
