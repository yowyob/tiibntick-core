package com.yowyob.tiibntick.core.tp.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import com.yowyob.tiibntick.core.tp.adapter.in.web.dto.response.LoyaltyAccountResponse;
import com.yowyob.tiibntick.core.tp.adapter.in.web.mapper.TntTpWebMapper;
import com.yowyob.tiibntick.core.tp.application.port.in.command.EarnLoyaltyPointsCommand;
import com.yowyob.tiibntick.core.tp.application.port.in.command.RedeemLoyaltyPointsCommand;
import com.yowyob.tiibntick.core.tp.application.service.LoyaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for loyalty program management.
 * Base path: /api/v1/tnt-tp/loyalty
 *
 * <h3>Security ()</h3>
 * <p>Tenant identity resolved from JWT via {@code @CurrentUser TntUserIdentity}.
 * Earning points ({@code billing:write}) is an internal operation called after
 * delivery completion. Redeeming points ({@code billing:write}) requires the
 * same permission — only the account owner or an admin may redeem.
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/tnt-tp/loyalty")
@Tag(name = "TiiBnTick Loyalty",
        description = "Loyalty points earn, redeem, and account management")
@SecurityRequirement(name = "bearerAuth")
public class LoyaltyController {

    private final LoyaltyService loyaltyService;
    private final TntTpWebMapper mapper;

    public LoyaltyController(LoyaltyService loyaltyService, TntTpWebMapper mapper) {
        this.loyaltyService = loyaltyService;
        this.mapper = mapper;
    }

    /**
     * Gets the loyalty account for a third party.
     * Requires permission: {@code billing:read}.
     */
    @GetMapping("/{thirdPartyId}")
    @Operation(summary = "Get loyalty account for a third party")
    @RequirePermission(resource = "billing", action = "read")
    public Mono<LoyaltyAccountResponse> getAccount(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID thirdPartyId) {
        return loyaltyService.getByThirdPartyId(currentUser.tenantId(), thirdPartyId)
                .map(mapper::toResponse);
    }

    /**
     * Credits loyalty points (internal operation — called after delivery completion).
     * Requires permission: {@code billing:write}.
     */
    @PostMapping("/{thirdPartyId}/earn")
    @Operation(summary = "Credit loyalty points (internal — called after delivery completion)")
    @RequirePermission(resource = "billing", action = "write")
    public Mono<LoyaltyAccountResponse> earn(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID thirdPartyId,
            @RequestParam int points,
            @RequestParam String missionId) {
        EarnLoyaltyPointsCommand command =
                new EarnLoyaltyPointsCommand(currentUser.tenantId(), thirdPartyId, points, missionId);
        return loyaltyService.earn(command).map(mapper::toResponse);
    }

    /**
     * Redeems loyalty points for a discount on a delivery invoice.
     * Requires permission: {@code billing:write}.
     */
    @PostMapping("/{thirdPartyId}/redeem")
    @Operation(summary = "Redeem loyalty points for a discount on a delivery invoice")
    @RequirePermission(resource = "billing", action = "write")
    public Mono<LoyaltyAccountResponse> redeem(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID thirdPartyId,
            @RequestParam int points,
            @RequestParam String invoiceId) {
        RedeemLoyaltyPointsCommand command =
                new RedeemLoyaltyPointsCommand(currentUser.tenantId(), thirdPartyId, points, invoiceId);
        return loyaltyService.redeem(command).map(mapper::toResponse);
    }
}
