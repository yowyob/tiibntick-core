package com.yowyob.tiibntick.core.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryAnnouncementRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.delivery.domain.exception.AnnouncementNotFoundException;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryNotFoundException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service for delivery and announcement read operations.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryQueryService implements DeliveryQueryUseCase {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAnnouncementRepository announcementRepository;

    @Override
    public Mono<Delivery> findDeliveryById(UUID tenantId, UUID deliveryId) {
        return deliveryRepository.findById(tenantId, deliveryId)
                .switchIfEmpty(Mono.error(new DeliveryNotFoundException(deliveryId)));
    }

    @Override
    public Mono<Delivery> findByTrackingCode(String trackingCode) {
        return deliveryRepository.findByTrackingCode(trackingCode)
                .switchIfEmpty(Mono.error(new DeliveryNotFoundException(trackingCode)));
    }

    @Override
    public Flux<Delivery> findDeliveriesBySender(UUID tenantId, UUID senderId) {
        return deliveryRepository.findBySenderId(tenantId, senderId);
    }

    @Override
    public Flux<Delivery> findDeliveriesByDeliveryPerson(UUID tenantId, UUID deliveryPersonId) {
        return deliveryRepository.findByDeliveryPersonId(tenantId, deliveryPersonId);
    }

    @Override
    public Flux<Delivery> findDeliveriesByStatus(UUID tenantId, DeliveryStatus status) {
        return deliveryRepository.findByStatus(tenantId, status);
    }

    @Override
    public Flux<DeliveryAnnouncement> findAnnouncementsByClient(UUID tenantId, UUID clientId) {
        return announcementRepository.findByClientId(tenantId, clientId);
    }

    @Override
    public Mono<DeliveryAnnouncement> findAnnouncementById(UUID tenantId, UUID announcementId) {
        return announcementRepository.findById(tenantId, announcementId)
                .switchIfEmpty(Mono.error(
                    new AnnouncementNotFoundException(announcementId)));
    }

    @Override
    public Flux<DeliveryAnnouncement> findOpenAnnouncements(UUID tenantId) {
        return announcementRepository.findOpenAnnouncements(tenantId);
    }

    @Override
    public Flux<Delivery> listByFreelancerOrgId(String freelancerOrgId) {
        log.debug("Listing deliveries for FreelancerOrg={}", freelancerOrgId);
        return deliveryRepository.findByFreelancerOrgId(freelancerOrgId);
    }
}