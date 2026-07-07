package com.yowyob.tiibntick.core.delivery.adapter.in.web;

import com.yowyob.tiibntick.core.delivery.adapter.in.web.request.CreateAnnouncementRequest;
import com.yowyob.tiibntick.core.delivery.adapter.in.web.request.RespondToAnnouncementRequest;
import com.yowyob.tiibntick.core.delivery.adapter.in.web.response.DeliveryAnnouncementResponse;
import com.yowyob.tiibntick.core.delivery.adapter.in.web.response.DeliveryAnnouncementResponseMapper;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryAnnouncementUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.RespondToAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.SelectAnnouncementResponseCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for delivery announcement management.
 * Exposes TiiBnPick-style announcement lifecycle endpoints.
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Delivery Announcements", description = "TiiBnPick — Client delivery announcements and driver bidding")
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/delivery-announcements")
@RequiredArgsConstructor
public class DeliveryAnnouncementController {

    private final DeliveryAnnouncementUseCase announcementUseCase;
    private final DeliveryQueryUseCase queryUseCase;

    @Operation(summary = "Publish a new delivery announcement")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DeliveryAnnouncementResponse> publish(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateAnnouncementRequest req) {

        CreateDeliveryAnnouncementCommand cmd = req.toCommand(tenantId);
        return announcementUseCase.publishAnnouncement(cmd)
                .map(DeliveryAnnouncementResponseMapper::toResponse);
    }

    @Operation(summary = "Get announcement by ID")
    @GetMapping("/{announcementId}")
    public Mono<DeliveryAnnouncementResponse> getById(
            @PathVariable UUID tenantId,
            @PathVariable UUID announcementId) {
        return queryUseCase.findAnnouncementById(tenantId, announcementId)
                .map(DeliveryAnnouncementResponseMapper::toResponse);
    }

    @Operation(summary = "List open announcements (PUBLISHED or IN_NEGOTIATION)")
    @GetMapping("/open")
    public Flux<DeliveryAnnouncementResponse> listOpen(@PathVariable UUID tenantId) {
        return queryUseCase.findOpenAnnouncements(tenantId)
                .map(DeliveryAnnouncementResponseMapper::toResponse);
    }

    @Operation(summary = "List announcements by client")
    @GetMapping("/client/{clientId}")
    public Flux<DeliveryAnnouncementResponse> listByClient(
            @PathVariable UUID tenantId,
            @PathVariable UUID clientId) {
        return queryUseCase.findAnnouncementsByClient(tenantId, clientId)
                .map(DeliveryAnnouncementResponseMapper::toResponse);
    }

    @Operation(summary = "Delivery person responds to an announcement")
    @PostMapping("/{announcementId}/responses")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DeliveryAnnouncementResponse> respond(
            @PathVariable UUID tenantId,
            @PathVariable UUID announcementId,
            @Valid @RequestBody RespondToAnnouncementRequest req) {

        RespondToAnnouncementCommand cmd = new RespondToAnnouncementCommand(
                tenantId, announcementId, req.deliveryPersonId(),
                req.estimatedArrivalTime(), req.note());
        return announcementUseCase.respondToAnnouncement(cmd)
                .map(DeliveryAnnouncementResponseMapper::toResponse);
    }

    @Operation(summary = "Client selects a delivery person's response")
    @PostMapping("/{announcementId}/responses/{responseId}/select")
    public Mono<DeliveryAnnouncementResponse> selectResponse(
            @PathVariable UUID tenantId,
            @PathVariable UUID announcementId,
            @PathVariable UUID responseId,
            @RequestHeader("X-Client-Id") UUID clientId) {

        SelectAnnouncementResponseCommand cmd = new SelectAnnouncementResponseCommand(
                tenantId, announcementId, clientId, responseId);
        return announcementUseCase.selectResponse(cmd)
                .map(DeliveryAnnouncementResponseMapper::toResponse);
    }

    @Operation(summary = "Client cancels their announcement")
    @DeleteMapping("/{announcementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> cancel(
            @PathVariable UUID tenantId,
            @PathVariable UUID announcementId,
            @RequestHeader("X-Client-Id") UUID clientId) {
        return announcementUseCase.cancelAnnouncement(tenantId, announcementId, clientId);
    }
}
