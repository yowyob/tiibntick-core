package com.yowyob.tiibntick.core.tp.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import com.yowyob.tiibntick.core.tp.adapter.in.web.dto.request.SubmitKycRequest;
import com.yowyob.tiibntick.core.tp.adapter.in.web.dto.response.KycRecordResponse;
import com.yowyob.tiibntick.core.tp.adapter.in.web.mapper.TntTpWebMapper;
import com.yowyob.tiibntick.core.tp.application.port.in.command.ApproveKycCommand;
import com.yowyob.tiibntick.core.tp.application.port.in.command.RejectKycCommand;
import com.yowyob.tiibntick.core.tp.application.port.in.command.SubmitKycCommand;
import com.yowyob.tiibntick.core.tp.application.service.TpKycService;
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
 * REST controller for KYC management.
 * Base path: /api/v1/tnt-tp/kyc
 *
 * <h3>Security ()</h3>
 * <p>Tenant identity is resolved from the JWT security context via
 * {@code @CurrentUser TntUserIdentity} (tnt-auth-core), replacing the legacy
 * {@code @RequestHeader("X-Tenant-Id")} pattern.
 * The reviewer admin ID is also resolved from the current user's actorId
 * rather than a manual header, since KYC approval is a privileged operation
 * requiring the {@code actor:approve} permission.
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/tnt-tp/kyc")
@Tag(name = "TiiBnTick KYC", description = "KYC document submission and verification")
@SecurityRequirement(name = "bearerAuth")
public class TpKycController {

    private final TpKycService kycService;
    private final TntTpWebMapper mapper;

    public TpKycController(TpKycService kycService, TntTpWebMapper mapper) {
        this.kycService = kycService;
        this.mapper = mapper;
    }

    /**
     * Submits KYC documents for a third party.
     * Requires permission: {@code actor:write}.
     * The tenantId is resolved from the JWT context.
     */
    @PostMapping("/{thirdPartyId}/submit")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit KYC documents for a third party")
    @RequirePermission(resource = "actor", action = "write")
    public Mono<KycRecordResponse> submit(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID thirdPartyId,
            @Valid @RequestBody SubmitKycRequest request) {
        SubmitKycCommand command = new SubmitKycCommand(
                currentUser.tenantId(), thirdPartyId,
                request.documentType(), request.documentStorageKey(),
                request.selfieStorageKey(), request.documentNumber(),
                request.documentExpiryDate());
        return kycService.submit(command).map(mapper::toResponse);
    }

    /**
     * Approves a submitted KYC record (admin operation).
     * Requires permission: {@code actor:approve}.
     * The reviewer adminId is derived from the current user's actorId.
     */
    @PostMapping("/{kycRecordId}/approve")
    @Operation(summary = "Approve a submitted KYC record (admin only)")
    @RequirePermission(resource = "actor", action = "approve")
    public Mono<KycRecordResponse> approve(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID kycRecordId) {
        ApproveKycCommand command = new ApproveKycCommand(
                currentUser.tenantId(), kycRecordId, currentUser.actorId());
        return kycService.approve(command).map(mapper::toResponse);
    }

    /**
     * Rejects a submitted KYC record with an explanatory reason (admin operation).
     * Requires permission: {@code actor:approve}.
     * The reviewer adminId is derived from the current user's actorId.
     */
    @PostMapping("/{kycRecordId}/reject")
    @Operation(summary = "Reject a submitted KYC record (admin only)")
    @RequirePermission(resource = "actor", action = "approve")
    public Mono<KycRecordResponse> reject(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID kycRecordId,
            @RequestParam String reason) {
        RejectKycCommand command = new RejectKycCommand(
                currentUser.tenantId(), kycRecordId, currentUser.actorId(), reason);
        return kycService.reject(command).map(mapper::toResponse);
    }

    /**
     * Gets the latest KYC record for a third party.
     * Requires permission: {@code actor:read}.
     */
    @GetMapping("/{thirdPartyId}/latest")
    @Operation(summary = "Get the latest KYC record for a third party")
    @RequirePermission(resource = "actor", action = "read")
    public Mono<KycRecordResponse> getLatest(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID thirdPartyId) {
        return kycService.getLatestByThirdPartyId(currentUser.tenantId(), thirdPartyId)
                .map(mapper::toResponse);
    }

    /**
     * Gets the full KYC history for a third party.
     * Requires permission: {@code actor:read}.
     */
    @GetMapping("/{thirdPartyId}/history")
    @Operation(summary = "Get the full KYC history for a third party")
    @RequirePermission(resource = "actor", action = "read")
    public Flux<KycRecordResponse> getHistory(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID thirdPartyId) {
        return kycService.getHistoryByThirdPartyId(currentUser.tenantId(), thirdPartyId)
                .map(mapper::toResponse);
    }
}
