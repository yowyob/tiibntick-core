package com.yowyob.tiibntick.core.organization.adapter.in.web;

import com.yowyob.tiibntick.core.organization.application.port.in.ManageHubUseCase;
import com.yowyob.tiibntick.core.organization.domain.model.HubRelais;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for relay hub (HubRelais) lifecycle management.
 *
 * <p>Relay hubs are intermediate parcel deposit/pickup points used in multi-leg
 * delivery scenarios. They carry spatial information (PostGIS point) and a capacity
 * ceiling for parcel storage.
 *
 * <p>URL structure: {@code /api/v1/tenants/{tenantId}/hubs}
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Hub Relais Management", description = "CRUD and capacity checks for relay hubs")
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/hubs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class HubRelaisController {

    private final ManageHubUseCase manageHubUseCase;

    // ─── Query endpoints ───────────────────────────────────────────────────────

    @Operation(summary = "List all relay hubs for a tenant")
    @GetMapping
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','BRANCH_MANAGER','RELAY_OPERATOR','TNT_ADMIN')")
    public Flux<HubRelais> listHubs(@PathVariable UUID tenantId) {
        return manageHubUseCase.listHubsForTenant(tenantId);
    }

    @Operation(summary = "Get relay hub by ID")
    @GetMapping("/{hubId}")
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','BRANCH_MANAGER','RELAY_OPERATOR','TNT_ADMIN')")
    public Mono<HubRelais> getHub(
            @PathVariable UUID tenantId,
            @PathVariable UUID hubId) {
        return manageHubUseCase.findHubById(OrganizationId.of(hubId));
    }

    @Operation(summary = "Find relay hubs within a geographic zone (PostGIS WKT polygon)")
    @GetMapping("/in-zone")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER','AGENCY_MANAGER','TNT_ADMIN')")
    public Flux<HubRelais> findHubsInZone(
            @PathVariable UUID tenantId,
            @RequestParam String polygonWkt) {
        return manageHubUseCase.findHubsInZone(polygonWkt);
    }

    @Operation(summary = "Check available capacity for a relay hub")
    @GetMapping("/{hubId}/capacity")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER','RELAY_OPERATOR','AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<Boolean> checkCapacity(
            @PathVariable UUID tenantId,
            @PathVariable UUID hubId,
            @RequestParam int currentOccupancy) {
        return manageHubUseCase.checkHubCapacity(OrganizationId.of(hubId), currentOccupancy);
    }

    // ─── Command endpoints ─────────────────────────────────────────────────────

    @Operation(summary = "Create a new relay hub")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<HubRelais> createHub(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateHubRequest request) {
        return manageHubUseCase.createHub(
                request.organizationId(),
                tenantId,
                request.name(),
                request.maxParcelCapacity(),
                request.geographicPointWkt(),
                request.openingHours(),
                request.operatorId());
    }

    @Operation(summary = "Update a relay hub's maximum parcel capacity")
    @PatchMapping("/{hubId}/capacity")
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<HubRelais> updateCapacity(
            @PathVariable UUID tenantId,
            @PathVariable UUID hubId,
            @RequestParam @Min(1) int maxParcelCapacity) {
        return manageHubUseCase.updateCapacity(OrganizationId.of(hubId), maxParcelCapacity);
    }

    @Operation(summary = "Assign or reassign a relay hub's operator")
    @PatchMapping("/{hubId}/operator")
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<HubRelais> assignOperator(
            @PathVariable UUID tenantId,
            @PathVariable UUID hubId,
            @RequestParam UUID operatorId) {
        return manageHubUseCase.assignOperator(OrganizationId.of(hubId), operatorId);
    }

    @Operation(summary = "Suspend a relay hub (temporarily out of service)")
    @PatchMapping("/{hubId}/suspend")
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<HubRelais> suspendHub(
            @PathVariable UUID tenantId,
            @PathVariable UUID hubId) {
        return manageHubUseCase.suspendHub(OrganizationId.of(hubId));
    }

    @Operation(summary = "Resume a suspended relay hub")
    @PatchMapping("/{hubId}/resume")
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<HubRelais> resumeHub(
            @PathVariable UUID tenantId,
            @PathVariable UUID hubId) {
        return manageHubUseCase.resumeHub(OrganizationId.of(hubId));
    }

    // ─── Request DTO ───────────────────────────────────────────────────────────

    /**
     * Request body for relay hub creation.
     *
     * @param organizationId      Kernel organization UUID (must be active in RT-comops)
     * @param name                Relay hub display name
     * @param maxParcelCapacity   Maximum parcel storage capacity (must be &gt; 0)
     * @param geographicPointWkt  PostGIS WKT POINT string — e.g. {@code POINT(9.7 4.05)}
     * @param openingHours        Free-text opening hours description
     * @param operatorId          Optional UUID of the operator actor
     */
    public record CreateHubRequest(
            UUID organizationId,
            @NotBlank String name,
            @Min(1) int maxParcelCapacity,
            @NotBlank String geographicPointWkt,
            String openingHours,
            UUID operatorId
    ) {}
}
