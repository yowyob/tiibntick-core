package com.yowyob.tiibntick.core.tp.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import com.yowyob.tiibntick.core.tp.adapter.in.web.dto.request.RegisterClientProfileRequest;
import com.yowyob.tiibntick.core.tp.adapter.in.web.dto.response.ClientProfileResponse;
import com.yowyob.tiibntick.core.tp.adapter.in.web.mapper.TntTpWebMapper;
import com.yowyob.tiibntick.core.tp.application.port.in.command.GeneratePhoneAliasCommand;
import com.yowyob.tiibntick.core.tp.application.port.in.command.RegisterTntClientProfileCommand;
import com.yowyob.tiibntick.core.tp.application.service.TntClientProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for TntClientProfile management.
 * Base path: /api/v1/tnt-tp/profiles
 *
 * <h3>Security ()</h3>
 * <p>Tenant identity is now resolved from the JWT security context via
 * {@code @CurrentUser TntUserIdentity} instead of the legacy
 * {@code @RequestHeader("X-Tenant-Id")} header, using {@code tnt-auth-core}.
 * Permission enforcement is declarative via {@code @RequirePermission}
 * from {@code tnt-roles-core}.
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/tnt-tp/profiles")
@Tag(name = "TiiBnTick Third Party Profiles",
        description = "Manage TiiBnTick client profiles (KYC, loyalty, phone masking)")
@SecurityRequirement(name = "bearerAuth")
public class TntClientProfileController {

    private final TntClientProfileService profileService;
    private final TntTpWebMapper mapper;

    public TntClientProfileController(
            TntClientProfileService profileService,
            TntTpWebMapper mapper) {
        this.profileService = profileService;
        this.mapper = mapper;
    }

    /**
     * Registers a new TiiBnTick client profile for an existing Kernel ThirdParty.
     * Requires permission: {@code actor:write}.
     * The tenantId is resolved from the authenticated user's JWT context.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new TiiBnTick client profile for an existing ThirdParty")
    @RequirePermission(resource = "actor", action = "write")
    public Mono<ClientProfileResponse> register(
            @CurrentUser TntUserIdentity currentUser,
            @Valid @RequestBody RegisterClientProfileRequest request) {
        RegisterTntClientProfileCommand command = new RegisterTntClientProfileCommand(
                currentUser.tenantId(), request.thirdPartyId(), request.roles(),
                request.preferredLocale(), request.preferredCurrency());
        return profileService.register(command).map(mapper::toResponse);
    }

    /**
     * Gets a client profile by its internal TiiBnTick UUID.
     * Requires permission: {@code actor:read}.
     */
    @GetMapping("/{profileId}")
    @Operation(summary = "Get a client profile by its UUID")
    @RequirePermission(resource = "actor", action = "read")
    public Mono<ClientProfileResponse> getById(
            @PathVariable UUID profileId) {
        return profileService.getByProfileId(profileId).map(mapper::toResponse);
    }

    /**
     * Gets a client profile by Kernel ThirdParty ID.
     * Requires permission: {@code actor:read}.
     * The tenantId is resolved from the JWT security context.
     */
    @GetMapping("/by-third-party/{thirdPartyId}")
    @Operation(summary = "Get a client profile by kernel ThirdParty ID")
    @RequirePermission(resource = "actor", action = "read")
    public Mono<ClientProfileResponse> getByThirdPartyId(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID thirdPartyId) {
        return profileService.getByThirdPartyId(currentUser.tenantId(), thirdPartyId)
                .map(mapper::toResponse);
    }

    /**
     * Generates a phone alias for relay-point anonymity.
     * Requires permission: {@code actor:write}.
     */
    @PostMapping("/{thirdPartyId}/phone-alias/generate")
    @Operation(summary = "Generate a phone alias for relay-point anonymity")
    @RequirePermission(resource = "actor", action = "write")
    public Mono<ClientProfileResponse> generatePhoneAlias(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID thirdPartyId) {
        GeneratePhoneAliasCommand command =
                new GeneratePhoneAliasCommand(currentUser.tenantId(), thirdPartyId);
        return profileService.generatePhoneAlias(command).map(mapper::toResponse);
    }

    /**
     * Removes a phone alias (reveals the real phone number).
     * Requires permission: {@code actor:write}.
     */
    @DeleteMapping("/{thirdPartyId}/phone-alias")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove phone alias (reveal real phone)")
    @RequirePermission(resource = "actor", action = "write")
    public Mono<ClientProfileResponse> removePhoneAlias(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID thirdPartyId) {
        return profileService.removePhoneAlias(currentUser.tenantId(), thirdPartyId)
                .map(mapper::toResponse);
    }
}
