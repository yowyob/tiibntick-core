package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AssignFreelancerRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RespondAnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.SubscriptionRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.SubscriptionResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AnnouncementUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound REST adapter for announcement management.
 * Orchestrates delivery-core announcement lifecycle via {@link AnnouncementUseCase}.
 *
 * @author MANFOUO BRAUN
 */
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementUseCase announcementUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AnnouncementResponseDTO> createAnnouncement(@RequestBody AnnouncementRequestDTO request) {
        return announcementUseCase.createAnnouncement(request);
    }

    @GetMapping
    public Flux<AnnouncementResponseDTO> getAllAnnouncements() {
        return announcementUseCase.getAllAnnouncements();
    }

    @GetMapping("/client/{clientId}")
    public Flux<AnnouncementResponseDTO> getAnnouncementsByClientId(@PathVariable UUID clientId) {
        return announcementUseCase.getAnnouncementsByClientId(clientId);
    }

    @GetMapping("/{id}")
    public Mono<AnnouncementResponseDTO> getAnnouncement(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID viewerFreelancerId) {
        if (viewerFreelancerId != null) {
            return announcementUseCase.getAnnouncementForCandidate(id, viewerFreelancerId);
        }
        return announcementUseCase.getAnnouncement(id);
    }

    @PutMapping("/{id}")
    public Mono<AnnouncementResponseDTO> updateAnnouncement(
            @PathVariable UUID id, @RequestBody AnnouncementRequestDTO request) {
        return announcementUseCase.updateAnnouncement(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteAnnouncement(@PathVariable UUID id) {
        return announcementUseCase.deleteAnnouncement(id);
    }

    @PatchMapping("/{id}/publish")
    public Mono<AnnouncementResponseDTO> publishAnnouncement(@PathVariable UUID id) {
        return announcementUseCase.publishAnnouncement(id);
    }

    @PostMapping("/{id}/respond")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AnnouncementResponseDTO> respond(
            @PathVariable("id") UUID announcementId,
            @RequestBody RespondAnnouncementRequestDTO request) {
        return announcementUseCase.respondToAnnouncement(announcementId, request);
    }

    @PostMapping("/{id}/subscribe")
    public Mono<ResponseEntity<Void>> subscribe(
            @PathVariable("id") UUID announcementId,
            @RequestBody SubscriptionRequestDTO request) {
        RespondAnnouncementRequestDTO respond = new RespondAnnouncementRequestDTO();
        respond.setFreelancerId(request.getFreelancerId());
        return announcementUseCase.respondToAnnouncement(announcementId, respond)
                .then(Mono.just(ResponseEntity.accepted().<Void>build()));
    }

    @GetMapping("/{id}/subscriptions")
    public Flux<SubscriptionResponseDTO> getSubscriptions(@PathVariable("id") UUID announcementId) {
        return announcementUseCase.getSubscriptionsForAnnouncement(announcementId);
    }

    @PostMapping("/{id}/assign")
    public Mono<ResponseEntity<AnnouncementResponseDTO>> assignFreelancer(
            @PathVariable("id") UUID announcementId,
            @RequestBody AssignFreelancerRequestDTO request) {
        if (request.getResponseId() != null && request.getClientId() != null) {
            return announcementUseCase
                    .assignResponse(announcementId, request.getClientId(), request.getResponseId())
                    .map(ResponseEntity::ok);
        }
        return announcementUseCase.assignFreelancer(announcementId, request.getFreelancerId())
                .map(ResponseEntity::ok);
    }

    @GetMapping("/subscriptions/freelancer/{freelancerId}")
    public Flux<AnnouncementResponseDTO> getFreelancerSubscriptions(
            @PathVariable UUID freelancerId) {
        return announcementUseCase.getSubscriptionsByFreelancerId(freelancerId);
    }
}
