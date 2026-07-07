package com.yowyob.tiibntick.core.organization.adapter.in.web;

import com.yowyob.tiibntick.core.organization.application.port.in.ManageBranchUseCase;
import com.yowyob.tiibntick.core.organization.domain.enums.DeliveryZoneType;
import com.yowyob.tiibntick.core.organization.domain.enums.ZoneAccessDifficulty;
import com.yowyob.tiibntick.core.organization.domain.model.Branch;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import com.yowyob.tiibntick.core.organization.domain.vo.ServiceZone;
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
 * REST controller for Branch lifecycle management.
 *
 * <p>A Branch is the operational sub-unit of an Agency. It represents a physical
 * location or zone coverage area within which deliveries are handled.
 *
 * <p>URL structure: {@code /api/v1/tenants/{tenantId}/agencies/{agencyId}/branches}
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Branch Management", description = "CRUD operations on Branch aggregates")
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/agencies/{agencyId}/branches")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class BranchController {

    private final ManageBranchUseCase manageBranchUseCase;

    // ─── Query endpoints ───────────────────────────────────────────────────────

    @Operation(summary = "List all branches belonging to an agency")
    @GetMapping
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','BRANCH_MANAGER','TNT_ADMIN')")
    public Flux<Branch> listBranches(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId) {
        return manageBranchUseCase.findBranchesByAgency(OrganizationId.of(agencyId));
    }

    @Operation(summary = "Get branch by ID")
    @GetMapping("/{branchId}")
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','BRANCH_MANAGER','TNT_ADMIN')")
    public Mono<Branch> getBranch(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @PathVariable UUID branchId) {
        return manageBranchUseCase.findBranchById(OrganizationId.of(branchId));
    }

    // ─── Command endpoints ─────────────────────────────────────────────────────

    @Operation(summary = "Create a new branch under an agency")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<Branch> createBranch(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @Valid @RequestBody CreateBranchRequest request) {

        ServiceZone serviceZone = null;
        if (request.serviceZone() != null) {
            CreateBranchRequest.ServiceZoneRequest sz = request.serviceZone();
            serviceZone = ServiceZone.of(
                    sz.zoneName(),
                    sz.polygonBoundsWkt(),
                    sz.accessDifficulty() != null
                            ? ZoneAccessDifficulty.valueOf(sz.accessDifficulty())
                            : ZoneAccessDifficulty.LOW,
                    sz.zoneType() != null
                            ? DeliveryZoneType.valueOf(sz.zoneType())
                            : DeliveryZoneType.URBAN);
        }

        return manageBranchUseCase.createBranch(
                request.organizationId(),
                OrganizationId.of(agencyId),
                tenantId,
                request.name(),
                request.address(),
                serviceZone);
    }

    @Operation(summary = "Deactivate a branch (temporary closure)")
    @PostMapping("/{branchId}/deactivate")
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<Branch> deactivateBranch(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @PathVariable UUID branchId) {
        return manageBranchUseCase.deactivateBranch(OrganizationId.of(branchId));
    }

    // ─── Request DTOs ──────────────────────────────────────────────────────────

    /**
     * Request body for branch creation.
     *
     * @param organizationId Kernel organization UUID (must be active in RT-comops)
     * @param name           Branch operating name
     * @param address        Physical address (may be informal for African markets)
     * @param serviceZone    Optional geographic coverage zone
     */
    public record CreateBranchRequest(
            UUID organizationId,
            @NotBlank String name,
            String address,
            ServiceZoneRequest serviceZone
    ) {
        /**
         * Embedded service zone definition within the branch creation request.
         *
         * @param zoneName         Human-readable zone name (e.g., "Douala Akwa")
         * @param polygonBoundsWkt WKT polygon (SRID 4326)
         * @param accessDifficulty {@link ZoneAccessDifficulty} enum name (nullable, defaults to LOW)
         * @param zoneType         {@link DeliveryZoneType} enum name (nullable, defaults to URBAN)
         */
        public record ServiceZoneRequest(
                @NotBlank String zoneName,
                @NotBlank String polygonBoundsWkt,
                String accessDifficulty,
                String zoneType
        ) {}
    }
}
