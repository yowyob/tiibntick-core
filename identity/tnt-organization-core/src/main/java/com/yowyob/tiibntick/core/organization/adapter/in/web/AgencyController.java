package com.yowyob.tiibntick.core.organization.adapter.in.web;

import com.yowyob.tiibntick.core.organization.application.port.in.ManageAgencyUseCase;
import com.yowyob.tiibntick.core.organization.domain.model.Agency;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for Agency lifecycle management.
 *
 * <p>Agencies are the top-level organizational units in TiiBnTick's identity hierarchy
 * (Kernel org → Agency → Branch → Actor). This controller exposes CRUD operations
 * over the {@link ManageAgencyUseCase} application port.
 *
 * <p>URL structure: {@code /api/v1/tenants/{tenantId}/agencies}
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Agency Management", description = "CRUD operations on Agency aggregates")
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/agencies")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AgencyController {

    private final ManageAgencyUseCase manageAgencyUseCase;

    // ─── Query endpoints ───────────────────────────────────────────────────────

    @Operation(summary = "List all agencies for a tenant")
    @GetMapping
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','BRANCH_MANAGER','TNT_ADMIN')")
    public Flux<Agency> listAgencies(@PathVariable UUID tenantId) {
        return manageAgencyUseCase.listAgenciesForTenant(tenantId);
    }

    @Operation(summary = "Get agency by ID")
    @GetMapping("/{agencyId}")
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','BRANCH_MANAGER','TNT_ADMIN')")
    public Mono<Agency> getAgency(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId) {
        return manageAgencyUseCase.findAgencyById(OrganizationId.of(agencyId));
    }

    @Operation(summary = "List agencies by Kernel organization ID")
    @GetMapping("/by-organization/{organizationId}")
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','TNT_ADMIN')")
    public Flux<Agency> listByOrganization(
            @PathVariable UUID tenantId,
            @PathVariable UUID organizationId) {
        return manageAgencyUseCase.findAgenciesByOrganizationId(organizationId);
    }

    // ─── Command endpoints ─────────────────────────────────────────────────────

    @Operation(summary = "Create a new agency under a Kernel organization")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<Agency> createAgency(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateAgencyRequest request) {
        return manageAgencyUseCase.createAgency(
                request.organizationId(),
                tenantId,
                request.name(),
                request.commerceRegistryNumber(),
                request.primaryCurrency());
    }

    // ─── Request DTO ───────────────────────────────────────────────────────────

    /**
     * Request body for agency creation.
     *
     * @param organizationId         Kernel organization UUID (must be active in RT-comops)
     * @param name                   Agency operating name
     * @param commerceRegistryNumber National commerce registry number (nullable)
     * @param primaryCurrency        ISO 4217 currency code (null defaults to XAF)
     */
    public record CreateAgencyRequest(
            UUID organizationId,
            @NotBlank String name,
            String commerceRegistryNumber,
            String primaryCurrency
    ) {}
}
