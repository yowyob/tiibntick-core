package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
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
import org.springframework.http.codec.ServerSentEvent;
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

    /**
     * Identité de l'appelant, ou {@code null} si le contexte est absent.
     *
     * <p>{@code required = false} sur les paramètres {@code @CurrentUser} de ce contrôleur n'est
     * pas une porte dérobée : la chaîne de sécurité ({@code TntSecurityConfig @Order(20)}) refuse
     * les requêtes non authentifiées sur {@code /api/**} avant que le contrôleur soit atteint.
     * Un {@code null} passé ici est refusé par {@code requireTrackingOwnership} via
     * {@code Mono.error(AccessDeniedException)} depuis le lot 25.1. La valeur de
     * {@code required = false} est de rendre le chemin d'échec explicite et auditable dans le
     * service plutôt que d'émettre un 401 opaque depuis le résolveur Spring.
     */
    private static UUID callerId(TntSecurityContext ctx) {
        return ctx != null ? ctx.userId() : null;
    }
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
    public Mono<ResponseEntity<DeliveryTrackingDTO>> trackDeliveryByNeed(
            @PathVariable UUID deliveryNeedId,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return deliveryUseCase.trackDeliveryByNeed(deliveryNeedId, callerId(ctx))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
        // No onErrorResume: GlobalExceptionHandler handles both
        // IllegalArgumentException("not found" → 404) and AccessDeniedException (→ 403).
    }

    /**
     * SSE tracking stream for a delivery-need.
     *
     * <p><strong>Refus avant ouverture de la connexion.</strong>
     * {@link DeliveryUseCase#checkTrackingOwnership} est résolu comme un {@code Mono} AVANT
     * que la {@code ResponseEntity} soit construite. Si la garde échoue (403 ou 404), le
     * {@code GlobalExceptionHandler} renvoie le statut correct sans aucun frame SSE.
     * L'ancienne signature {@code Mono.just(ResponseEntity.ok().body(body))} construisait
     * la réponse immédiatement (200 commité avant la souscription du flux) — la garde
     * n'avait d'effet que dans {@code MockServerHttpResponse}, pas sur une socket réelle.
     * {@code trackDeliveryByNeedStream} n'est appelé que si le check passe, via
     * {@code Mono.fromSupplier} (évaluation paresseuse après {@code then}).
     */
    @GetMapping(value = "/tracking/stream/delivery-need/{deliveryNeedId}",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<ServerSentEvent<DeliveryTrackingDTO>>>> trackDeliveryByNeedStream(
            @PathVariable UUID deliveryNeedId,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        UUID caller = callerId(ctx);
        return deliveryUseCase.checkTrackingOwnership(deliveryNeedId, caller)
                .then(Mono.fromSupplier(() -> {
                    Flux<ServerSentEvent<DeliveryTrackingDTO>> body =
                            deliveryUseCase.trackDeliveryByNeedStream(deliveryNeedId, caller)
                                    .map(dto -> ServerSentEvent.<DeliveryTrackingDTO>builder(dto)
                                            .event("tracking")
                                            .build());
                    return ResponseEntity.ok()
                            .<Flux<ServerSentEvent<DeliveryTrackingDTO>>>contentType(MediaType.TEXT_EVENT_STREAM)
                            .body(body);
                }));
    }
    @GetMapping("/{id}/assistance")
    public Mono<ResponseEntity<com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryAssistanceDTO>> getDeliveryAssistance(@PathVariable UUID id) {
        return deliveryUseCase.getDeliveryAssistance(id).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
