package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryStatusApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryStatusUpdateDTO;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.DeliveryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryTrackingDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryUpdateDTO;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller for delivery management.
 * Provides endpoints for querying, updating, status transitions, and cancellation.
 *
 * @author François-Charles ATANGA
 */
@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class GofpDeliveryController {

    private final DeliveryUseCase deliveryUseCase;
    /** Handles status transitions with automatic RelayDeposit creation. */
    private final DeliveryStatusApplicationService deliveryStatusApplicationService;

    // ──────────────────── Queries ────────────────────

    @GetMapping("/{id}")
    public Mono<ResponseEntity<DeliveryResponseDTO>> getDeliveryById(@PathVariable UUID id) {
        return deliveryUseCase.getDeliveryById(id).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
    }
    @GetMapping("/announcement/{announcementId}")
    public Mono<ResponseEntity<DeliveryResponseDTO>> getDeliveryByAnnouncementId(@PathVariable UUID announcementId) {
        return deliveryUseCase.getDeliveryByAnnouncementId(announcementId).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
    }
    @GetMapping("/freelancer/{freelancerId}")
    public Flux<DeliveryResponseDTO> getDeliveriesByFreelancerId(@PathVariable UUID freelancerId) {
        return deliveryUseCase.getDeliveriesByFreelancerId(freelancerId);
    }
    @GetMapping("/status/{status}")
    public Flux<DeliveryResponseDTO> getDeliveriesByStatus(@PathVariable DeliveryStatus status) {
        return deliveryUseCase.getDeliveriesByStatus(status);
    }
    @PutMapping("/{id}")
    public Mono<ResponseEntity<DeliveryResponseDTO>> updateDelivery(@PathVariable UUID id, @RequestBody DeliveryUpdateDTO dto) {
        return deliveryUseCase.updateDelivery(id, dto).map(ResponseEntity::ok);
    }
    /**
     * Updates the delivery status.
     * If status == DELIVERED and relayPointId is provided, automatically creates a RelayDeposit.
     * If status == PICKED_UP or DELIVERED (direct), a confirmationCode is required.
     */
    @PatchMapping("/{id}/status")
    public Mono<ResponseEntity<Delivery>> updateStatus(@PathVariable UUID id,
                                                       @RequestBody DeliveryStatusUpdateDTO dto) {
        return deliveryStatusApplicationService.updateStatus(id, dto)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.badRequest().<Delivery>build()));
    }

    /**
     * Initialises OTP codes for a delivery (idempotent — no-op if already set).
     * Sends the pickup code to the shipper and the delivery code to the recipient.
     * Should be called once the delivery is created and the freelancer assigned.
     */
    @PostMapping("/{id}/init-otp")
    public Mono<ResponseEntity<Void>> initOtp(@PathVariable UUID id) {
        return deliveryUseCase.getDeliveryById(id)
                .flatMap(dto -> deliveryStatusApplicationService.initOtpForDelivery(id))
                .map(d -> ResponseEntity.ok().<Void>build())
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().<Void>build()));
    }
    @PatchMapping("/{id}/cancel")
    public Mono<ResponseEntity<DeliveryResponseDTO>> cancelDelivery(@PathVariable UUID id) {
        return deliveryUseCase.cancelDelivery(id).map(ResponseEntity::ok);
    }
    @GetMapping("/delivery-need/{deliveryNeedId}")
    public Mono<ResponseEntity<DeliveryResponseDTO>> getDeliveryByDeliveryNeedId(@PathVariable UUID deliveryNeedId) {
        return deliveryUseCase.getDeliveryByDeliveryNeedId(deliveryNeedId).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
    }
    @GetMapping("/tracking/announcement/{announcementId}")
    public Mono<ResponseEntity<DeliveryTrackingDTO>> trackDelivery(@PathVariable UUID announcementId) {
        return deliveryUseCase.trackDelivery(announcementId).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
    }
    @GetMapping("/tracking/stream/announcement/{announcementId}")
    public ResponseEntity<Flux<org.springframework.http.codec.ServerSentEvent<DeliveryTrackingDTO>>> trackDeliveryStream(
            @PathVariable UUID announcementId) {
        Flux<org.springframework.http.codec.ServerSentEvent<DeliveryTrackingDTO>> body =
                deliveryUseCase.trackDeliveryStream(announcementId)
                        .map(dto -> org.springframework.http.codec.ServerSentEvent
                                .<DeliveryTrackingDTO>builder(dto)
                                .event("tracking")
                                .build());
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(body);
    }
    @GetMapping("/tracking/delivery-need/{deliveryNeedId}")
    public Mono<ResponseEntity<DeliveryTrackingDTO>> trackDeliveryByNeed(@PathVariable UUID deliveryNeedId) {
        return deliveryUseCase.trackDeliveryByNeed(deliveryNeedId).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
    }
    @GetMapping("/tracking/stream/delivery-need/{deliveryNeedId}")
    public ResponseEntity<Flux<org.springframework.http.codec.ServerSentEvent<DeliveryTrackingDTO>>> trackDeliveryByNeedStream(
            @PathVariable UUID deliveryNeedId) {
        Flux<org.springframework.http.codec.ServerSentEvent<DeliveryTrackingDTO>> body =
                deliveryUseCase.trackDeliveryByNeedStream(deliveryNeedId)
                        .map(dto -> org.springframework.http.codec.ServerSentEvent
                                .<DeliveryTrackingDTO>builder(dto)
                                .event("tracking")
                                .build());
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(body);
    }
    @GetMapping("/{id}/assistance")
    public Mono<ResponseEntity<com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryAssistanceDTO>> getDeliveryAssistance(@PathVariable UUID id) {
        return deliveryUseCase.getDeliveryAssistance(id).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
