package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.SubscriptionResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.AnnouncementSubscription;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.AnnouncementUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.AnnouncementSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service implementing AnnouncementUseCase.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementApplicationService implements AnnouncementUseCase {

    private final IAnnouncementRepository announcementRepository;
    private final AnnouncementSubscriptionRepository subscriptionRepository;

    @Override
    public Mono<AnnouncementResponseDTO> createAnnouncement(AnnouncementRequestDTO request) {
        Announcement announcement = new Announcement();
        announcement.setId(UUID.randomUUID());
        announcement.setPaymentMethod(request.getPaymentMethod());
        announcement.setTransportMethod(request.getTransportMethod());
        announcement.setDistance(request.getDistance());
        announcement.setLogisticsPrice(request.getLogisticsPrice());
        announcement.setRequiredVehicleType(request.getRequiredVehicleType());
        announcement.setDestinationRelayPointId(request.getDestinationRelayPointId());
        
        announcement.setShipperFirstName(request.getShipperFirstName());
        announcement.setShipperLastName(request.getShipperLastName());
        announcement.setShipperEmail(request.getShipperEmail());
        announcement.setShipperPhone(request.getShipperPhone());
        
        announcement.setRecipientFirstName(request.getRecipientFirstName());
        announcement.setRecipientLastName(request.getRecipientLastName());
        announcement.setRecipientEmail(request.getRecipientEmail());
        announcement.setRecipientPhone(request.getRecipientPhone());
        announcement.setAmount(request.getAmount());
        announcement.setCurrency(request.getCurrency() != null ? request.getCurrency() : "XAF");
        
        return announcementRepository.save(announcement).map(this::toDTO);
    }

    @Override
    public Flux<AnnouncementResponseDTO> getAllAnnouncements() {
        return announcementRepository.findAll().map(this::toDTO);
    }

    @Override
    public Mono<AnnouncementResponseDTO> getAnnouncement(UUID id) {
        return announcementRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Announcement not found: " + id)))
                .map(this::toDTO);
    }

    @Override
    public Flux<AnnouncementResponseDTO> getAnnouncementsByClientId(UUID clientId) {
        return announcementRepository.findAllByClientId(clientId).map(this::toDTO);
    }

    @Override
    public Mono<AnnouncementResponseDTO> updateAnnouncement(UUID id, AnnouncementRequestDTO request) {
        return announcementRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Announcement not found: " + id)))
                .flatMap(a -> {
                    if (request.getPaymentMethod() != null)     a.setPaymentMethod(request.getPaymentMethod());
                    if (request.getTransportMethod() != null)   a.setTransportMethod(request.getTransportMethod());
                    if (request.getDistance() != null)          a.setDistance(request.getDistance());
                    if (request.getLogisticsPrice() != null)    a.setLogisticsPrice(request.getLogisticsPrice());
                    if (request.getRequiredVehicleType() != null) a.setRequiredVehicleType(request.getRequiredVehicleType());
                    
                    if (request.getShipperFirstName() != null)  a.setShipperFirstName(request.getShipperFirstName());
                    if (request.getShipperLastName() != null)   a.setShipperLastName(request.getShipperLastName());
                    if (request.getShipperEmail() != null)      a.setShipperEmail(request.getShipperEmail());
                    if (request.getShipperPhone() != null)      a.setShipperPhone(request.getShipperPhone());
                    
                    if (request.getRecipientFirstName() != null) a.setRecipientFirstName(request.getRecipientFirstName());
                    if (request.getRecipientLastName() != null)  a.setRecipientLastName(request.getRecipientLastName());
                    if (request.getRecipientEmail() != null)     a.setRecipientEmail(request.getRecipientEmail());
                    if (request.getRecipientPhone() != null)     a.setRecipientPhone(request.getRecipientPhone());
                    if (request.getAmount() != null)             a.setAmount(request.getAmount());
                    if (request.getCurrency() != null)           a.setCurrency(request.getCurrency());
                    
                    return announcementRepository.save(a);
                })
                .map(this::toDTO);
    }

    @Override
    public Mono<Void> deleteAnnouncement(UUID id) {
        return announcementRepository.deleteById(id);
    }

    @Override
    public Mono<AnnouncementResponseDTO> publishAnnouncement(UUID id) {
        return announcementRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Announcement not found: " + id)))
                .flatMap(a -> announcementRepository.save(a))
                .map(this::toDTO);
    }

    @Override
    public Mono<Void> initiateSubscription(UUID announcementId, UUID freelancerId) {
        AnnouncementSubscription sub = new AnnouncementSubscription();
        sub.setId(UUID.randomUUID());
        sub.setAnnouncementId(announcementId);
        sub.setFreelancerId(freelancerId);
        return subscriptionRepository.save(sub).then();
    }

    @Override
    public Flux<SubscriptionResponseDTO> getSubscriptionsForAnnouncement(UUID announcementId) {
        return subscriptionRepository.findAllByAnnouncementId(announcementId)
                .map(s -> {
                    SubscriptionResponseDTO dto = new SubscriptionResponseDTO();
                    dto.setSubscriptionId(s.getId());
                    dto.setFreelancerId(s.getFreelancerId());
                    dto.setStatus(s.getStatus());
                    dto.setCreatedAt(s.getCreatedAt());
                    return dto;
                });
    }

    @Override
    public Mono<AnnouncementResponseDTO> assignFreelancer(UUID announcementId, UUID freelancerId) {
        return announcementRepository.findById(announcementId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Announcement not found: " + announcementId)))
                .flatMap(a -> {
                    a.setAssignedFreelancerId(freelancerId);
                    return announcementRepository.save(a);
                })
                .map(this::toDTO);
    }

    @Override
    public Flux<AnnouncementResponseDTO> getSubscriptionsByFreelancerId(UUID freelancerId) {
        return announcementRepository.findAllByAssignedFreelancerId(freelancerId).map(this::toDTO);
    }

    private AnnouncementResponseDTO toDTO(Announcement a) {
        AnnouncementResponseDTO dto = new AnnouncementResponseDTO();
        dto.setId(a.getId());
        dto.setPaymentMethod(a.getPaymentMethod());
        dto.setTransportMethod(a.getTransportMethod());
        dto.setDistance(a.getDistance());
        dto.setLogisticsPrice(a.getLogisticsPrice());
        dto.setRequiredVehicleType(a.getRequiredVehicleType());
        dto.setDestinationRelayPointId(a.getDestinationRelayPointId());
        dto.setAssignedFreelancerId(a.getAssignedFreelancerId());
        
        dto.setShipperFirstName(a.getShipperFirstName());
        dto.setShipperLastName(a.getShipperLastName());
        dto.setShipperEmail(a.getShipperEmail());
        dto.setShipperPhone(a.getShipperPhone());
        
        dto.setRecipientFirstName(a.getRecipientFirstName());
        dto.setRecipientLastName(a.getRecipientLastName());
        dto.setRecipientEmail(a.getRecipientEmail());
        dto.setRecipientPhone(a.getRecipientPhone());
        dto.setAmount(a.getAmount());
        dto.setCurrency(a.getCurrency());
        
        return dto;
    }
}
