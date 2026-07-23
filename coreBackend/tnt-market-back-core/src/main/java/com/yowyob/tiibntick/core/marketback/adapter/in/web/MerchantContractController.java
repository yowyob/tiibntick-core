package com.yowyob.tiibntick.core.marketback.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageMerchantContractUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.InitContractNegotiationCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.RenewContractCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.MerchantContractResponse;
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
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Generic Market API for merchant volume contracts — negotiation, dual
 * signature (merchant + provider) and renewal of B2B delivery contracts.
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Market Merchant Contracts", description = "Volume contracts between e-commerce merchants and logistics providers")
@RestController
@RequestMapping("/api/v1/platform/market/contracts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MerchantContractController {

    private final IManageMerchantContractUseCase contractUseCase;

    @Operation(summary = "Initiate a merchant/provider volume contract negotiation")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MerchantContractResponse> initiate(@Valid @RequestBody InitContractNegotiationCommand command) {
        return contractUseCase.initNegotiation(command);
    }

    @Operation(summary = "Sign the contract as the merchant (caller's own identity)")
    @PostMapping("/{id}/sign")
    @PreAuthorize("isAuthenticated()")
    public Mono<MerchantContractResponse> sign(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        UUID merchantId = currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
        return contractUseCase.signByMerchant(id, merchantId, currentUser.tenantId().toString());
    }

    @Operation(summary = "Countersign the contract as the provider (caller's own identity)")
    @PostMapping("/{id}/countersign")
    @PreAuthorize("isAuthenticated()")
    public Mono<MerchantContractResponse> countersign(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        UUID providerId = currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
        return contractUseCase.countersignByProvider(id, providerId, currentUser.tenantId().toString());
    }

    @Operation(summary = "Renew a contract with a new end date")
    @PostMapping("/{id}/renew")
    @PreAuthorize("isAuthenticated()")
    public Mono<MerchantContractResponse> renew(
            @PathVariable UUID id,
            @Valid @RequestBody RenewContractCommand command,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return contractUseCase.renewContract(id, command, currentUser.tenantId().toString());
    }

    @Operation(summary = "Terminate a contract")
    @PostMapping("/{id}/terminate")
    @PreAuthorize("isAuthenticated()")
    public Mono<MerchantContractResponse> terminate(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return contractUseCase.terminateContract(id, reason, currentUser.tenantId().toString());
    }

    @Operation(summary = "Get a contract by id")
    @GetMapping("/{id}")
    public Mono<MerchantContractResponse> get(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return contractUseCase.getContract(id, currentUser.tenantId().toString());
    }

    @Operation(summary = "List contracts for a merchant")
    @GetMapping("/by-merchant/{merchantId}")
    public Flux<MerchantContractResponse> getByMerchant(
            @PathVariable UUID merchantId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return contractUseCase.getMerchantContracts(merchantId, currentUser.tenantId().toString());
    }

    @Operation(summary = "List contracts for a provider")
    @GetMapping("/by-provider/{providerId}")
    public Flux<MerchantContractResponse> getByProvider(
            @PathVariable UUID providerId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return contractUseCase.getProviderContracts(providerId, currentUser.tenantId().toString());
    }
}
