package com.yowyob.tiibntick.core.linkback.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.RespondToAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.SelectAnnouncementResponseCommand;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.request.BidOnBoardEntryRequest;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.request.PublishBoardEntryRequest;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.BoardEntryResponse;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.BoardEntryResponseMapper;
import com.yowyob.tiibntick.core.linkback.application.port.in.ManageLinkBoardUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryLinkBoardUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Generic Link business API for the delivery bulletin board — a thin façade over
 * tnt-delivery-core's {@code DeliveryAnnouncementUseCase}/{@code DeliveryQueryUseCase}.
 * The single entry point the Link BFF calls; must never be bypassed in favour of
 * calling tnt-delivery-core directly (see coreBackend architecture decision).
 *
 * @author Dilane PAFE
 */
@Tag(name = "Link Board", description = "Delivery bulletin board / bidding for the TiiBnTick Link network")
@RestController
@RequestMapping("/api/v1/platform/link/board")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class LinkBoardController {

    private final ManageLinkBoardUseCase manageUseCase;
    private final QueryLinkBoardUseCase queryUseCase;

    @Operation(summary = "Publish a new delivery announcement to the board")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<BoardEntryResponse> publish(
            @Valid @RequestBody PublishBoardEntryRequest request,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        var command = new CreateDeliveryAnnouncementCommand(
                currentUser.tenantId(),
                resolveActorId(currentUser),
                request.title(),
                request.description(),
                request.offeredAmount(),
                request.currency(),
                request.packageSpec(),
                request.pickupAddress(),
                request.deliveryAddress(),
                request.recipient(),
                request.urgency());
        return manageUseCase.publish(command).map(BoardEntryResponseMapper::toResponse);
    }

    @Operation(summary = "Bid on an open board entry")
    @PostMapping("/{announcementId}/bid")
    @PreAuthorize("isAuthenticated()")
    public Mono<BoardEntryResponse> bid(
            @PathVariable UUID announcementId,
            @Valid @RequestBody BidOnBoardEntryRequest request,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        var command = new RespondToAnnouncementCommand(
                currentUser.tenantId(), announcementId, resolveActorId(currentUser),
                request.estimatedArrivalTime(), request.note());
        return manageUseCase.bid(command).map(BoardEntryResponseMapper::toResponse);
    }

    @Operation(summary = "Elect a delivery person's bid, creating the delivery")
    @PostMapping("/{announcementId}/elect/{responseId}")
    @PreAuthorize("isAuthenticated()")
    public Mono<BoardEntryResponse> elect(
            @PathVariable UUID announcementId,
            @PathVariable UUID responseId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        var command = new SelectAnnouncementResponseCommand(
                currentUser.tenantId(), announcementId, resolveActorId(currentUser), responseId);
        return manageUseCase.elect(command).map(BoardEntryResponseMapper::toResponse);
    }

    @Operation(summary = "Cancel a board entry")
    @DeleteMapping("/{announcementId}")
    @PreAuthorize("isAuthenticated()")
    public Mono<Void> cancel(
            @PathVariable UUID announcementId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return manageUseCase.cancel(currentUser.tenantId(), announcementId, resolveActorId(currentUser));
    }

    @Operation(summary = "Get a board entry by id")
    @GetMapping("/{announcementId}")
    public Mono<BoardEntryResponse> getById(
            @PathVariable UUID announcementId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return queryUseCase.findById(currentUser.tenantId(), announcementId)
                .map(BoardEntryResponseMapper::toResponse);
    }

    @Operation(summary = "List all open board entries")
    @GetMapping("/open")
    public Flux<BoardEntryResponse> open(@Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return queryUseCase.findOpen(currentUser.tenantId()).map(BoardEntryResponseMapper::toResponse);
    }

    @Operation(summary = "List my own published board entries")
    @GetMapping("/mine")
    public Flux<BoardEntryResponse> mine(@Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return queryUseCase.findMine(currentUser.tenantId(), resolveActorId(currentUser))
                .map(BoardEntryResponseMapper::toResponse);
    }

    private UUID resolveActorId(TntUserIdentity currentUser) {
        return currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
    }
}
