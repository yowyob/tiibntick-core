package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementResponseDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port — announcement use cases exposed by Layer 6 to the BFF.
 */
public interface IAnnouncementUseCase {

    Mono<AnnouncementResponseDTO> createAnnouncement(AnnouncementRequestDTO request);

    Flux<AnnouncementResponseDTO> getAllAnnouncements();

    Mono<AnnouncementResponseDTO> getAnnouncementById(UUID id);

    Flux<AnnouncementResponseDTO> getAnnouncementsByClientId(UUID clientId);

    Mono<AnnouncementResponseDTO> updateAnnouncement(UUID id, AnnouncementRequestDTO request);

    Mono<Void> deleteAnnouncement(UUID id);

    Mono<AnnouncementResponseDTO> publishAnnouncement(UUID id);

    Mono<Void> initiateSubscription(UUID announcementId, UUID freelancerId);

    Mono<AnnouncementResponseDTO> assignFreelancer(UUID announcementId, UUID freelancerId);

    Flux<AnnouncementResponseDTO> getAnnouncementsByFreelancerId(UUID freelancerId);
}
