package com.yowyob.tiibntick.core.marketback.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageMarketCampaignUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.CreateCampaignCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.MarketCampaignResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Generic Market API for promotional campaigns — admin-managed promo codes,
 * flash sales and seasonal discounts applied at checkout.
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Market Campaigns", description = "Promotional campaigns managed by TiiBnTick Market admins")
@RestController
@RequestMapping("/api/v1/platform/market/campaigns")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MarketCampaignController {

    private final IManageMarketCampaignUseCase campaignUseCase;

    @Operation(summary = "Create a promotional campaign")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MarketCampaignResponse> create(@Valid @RequestBody CreateCampaignCommand command) {
        return campaignUseCase.createCampaign(command);
    }

    @Operation(summary = "Activate a campaign")
    @PostMapping("/{id}/activate")
    public Mono<MarketCampaignResponse> activate(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return campaignUseCase.activateCampaign(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "Pause a campaign")
    @PostMapping("/{id}/pause")
    public Mono<MarketCampaignResponse> pause(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return campaignUseCase.pauseCampaign(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "Terminate a campaign")
    @PostMapping("/{id}/end")
    public Mono<MarketCampaignResponse> end(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return campaignUseCase.terminateCampaign(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "Validate a promo code against an order")
    @PostMapping("/promo-code/{code}/validate")
    public Mono<MarketCampaignResponse> validatePromoCode(
            @PathVariable String code,
            @RequestParam UUID orderId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return campaignUseCase.validatePromoCode(code, orderId, currentUser.tenantId().toString());
    }

    @Operation(summary = "Get a campaign by id")
    @GetMapping("/{id}")
    public Mono<MarketCampaignResponse> get(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return campaignUseCase.getCampaign(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "List active campaigns for the caller's tenant")
    @GetMapping("/active")
    public Flux<MarketCampaignResponse> getActive(@Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return campaignUseCase.getActiveCampaigns(currentUser.tenantId().toString());
    }

    @Operation(summary = "List all campaigns for the caller's tenant")
    @GetMapping
    public Flux<MarketCampaignResponse> getAll(@Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return campaignUseCase.getAllCampaigns(currentUser.tenantId().toString());
    }
}
