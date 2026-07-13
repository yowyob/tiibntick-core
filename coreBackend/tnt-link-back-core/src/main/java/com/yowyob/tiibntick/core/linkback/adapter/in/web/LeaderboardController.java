package com.yowyob.tiibntick.core.linkback.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.LeaderboardEntryResponse;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.LeaderboardEntryResponseMapper;
import com.yowyob.tiibntick.core.linkback.application.port.in.GetLeaderboardUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Generic Link business API for the network leaderboard — a read-only ranking
 * over {@code NetworkNode} (trust score, then gamification points), not a new
 * domain in its own right.
 *
 * @author Dilane PAFE
 */
@Tag(name = "Link Leaderboard", description = "Trust/gamification ranking for the TiiBnTick Link network")
@RestController
@RequestMapping("/api/v1/platform/link/leaderboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class LeaderboardController {

    private final GetLeaderboardUseCase getLeaderboardUseCase;

    @Operation(summary = "Get the top-ranked network nodes")
    @GetMapping
    public Flux<LeaderboardEntryResponse> top(
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return getLeaderboardUseCase.getTopNodes(currentUser.tenantId(), limit)
                .index((rank, node) -> LeaderboardEntryResponseMapper.toResponse(rank.intValue() + 1, node));
    }
}
