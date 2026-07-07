package com.yowyob.tiibntick.core.tp.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import com.yowyob.tiibntick.core.tp.adapter.in.web.dto.request.RateThirdPartyRequest;
import com.yowyob.tiibntick.core.tp.adapter.in.web.dto.response.RatingResponse;
import com.yowyob.tiibntick.core.tp.adapter.in.web.mapper.TntTpWebMapper;
import com.yowyob.tiibntick.core.tp.application.port.in.command.RateThirdPartyCommand;
import com.yowyob.tiibntick.core.tp.application.service.RatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for third party rating management.
 * Base path: /api/v1/tnt-tp/ratings
 *
 * <h3>Security ()</h3>
 * <p>Tenant identity resolved from JWT via {@code @CurrentUser TntUserIdentity}.
 * The rater actor ID is also resolved from the current user's JWT context.
 * Permission enforcement via {@code @RequirePermission} (tnt-roles-core).
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/tnt-tp/ratings")
@Tag(name = "TiiBnTick Ratings",
        description = "Rate third parties (senders, recipients, deliverers) after a mission")
@SecurityRequirement(name = "bearerAuth")
public class RatingController {

    private final RatingService ratingService;
    private final TntTpWebMapper mapper;

    public RatingController(RatingService ratingService, TntTpWebMapper mapper) {
        this.ratingService = ratingService;
        this.mapper = mapper;
    }

    /**
     * Submits a rating for a third party after a delivery mission.
     * The raterActorId is resolved from the current user's JWT actorId.
     * Requires permission: {@code actor:write}.
     */
    @PostMapping("/{thirdPartyId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a rating for a third party after a delivery mission")
    @RequirePermission(resource = "actor", action = "write")
    public Mono<RatingResponse> rate(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID thirdPartyId,
            @Valid @RequestBody RateThirdPartyRequest request) {
        RateThirdPartyCommand command = new RateThirdPartyCommand(
                currentUser.tenantId(), thirdPartyId,
                currentUser.actorId() != null ? currentUser.actorId() : request.raterActorId(),
                request.missionId(), request.score(), request.comment());
        return ratingService.rate(command).map(mapper::toResponse);
    }

    /**
     * Lists all ratings for a third party.
     * Requires permission: {@code actor:read}.
     */
    @GetMapping("/{thirdPartyId}")
    @Operation(summary = "List all ratings for a third party")
    @RequirePermission(resource = "actor", action = "read")
    public Flux<RatingResponse> listRatings(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID thirdPartyId) {
        return ratingService.listRatings(currentUser.tenantId(), thirdPartyId)
                .map(mapper::toResponse);
    }
}
