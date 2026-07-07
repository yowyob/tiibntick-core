package com.yowyob.tiibntick.core.geo.adapter.in.web;

import com.yowyob.tiibntick.core.geo.application.port.in.*;
import com.yowyob.tiibntick.core.geo.domain.model.*;
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

import java.util.List;
import java.util.UUID;

/**
 * REST controller for geographic services.
 *
 * <p>Exposes geocoding, reverse-geocoding, nearby-hub search, geofence checks,
 * and point-of-interest management. Backed by Nominatim OSM with a local African
 * landmark fallback (tnt-geo-core).
 *
 * <p>URL prefix: {@code /api/v1/geo}
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Geo Services", description = "Geocoding, hub search, geofencing and POI management")
@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class GeoController {

    private final IGeocodeUseCase geocodeUseCase;
    private final IFindNearbyHubsUseCase findNearbyHubsUseCase;
    private final IGeofenceUseCase geofenceUseCase;
    private final IManagePointsOfInterestUseCase managePoisUseCase;
    private final IFreelancerOrgGeoUseCase freelancerOrgGeoUseCase;

    // ─── Geocoding ─────────────────────────────────────────────────────────────

    @Operation(summary = "Convert an address to geographic coordinates (forward geocoding)")
    @GetMapping("/geocode")
    @PreAuthorize("isAuthenticated()")
    public Mono<AddressResult> geocode(
            @RequestParam String address,
            @RequestParam(required = false, defaultValue = "") String cityCode) {
        return geocodeUseCase.geocode(address, cityCode);
    }

    @Operation(summary = "Convert coordinates to a human-readable address (reverse geocoding)")
    @GetMapping("/reverse")
    @PreAuthorize("isAuthenticated()")
    public Mono<AddressResult> reverseGeocode(
            @RequestParam double latitude,
            @RequestParam double longitude) {
        return geocodeUseCase.reverseGeocode(GeoPoint.of(latitude, longitude));
    }

    @Operation(summary = "Find registered addresses / POIs near a geographic point")
    @GetMapping("/nearby/addresses")
    @PreAuthorize("isAuthenticated()")
    public Flux<AddressResult> findNearbyAddresses(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5.0") double radiusKm,
            @RequestParam(required = false) String tenantId) {
        return geocodeUseCase.findNearbyAddresses(
                GeoPoint.of(latitude, longitude), radiusKm, tenantId);
    }

    // ─── Relay hub search ──────────────────────────────────────────────────────

    @Operation(summary = "Find available relay hubs near a geographic point")
    @GetMapping("/tenants/{tenantId}/hubs/nearby")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER','AGENCY_MANAGER','TNT_ADMIN')")
    public Flux<RelayHub> findNearbyHubs(
            @PathVariable UUID tenantId,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "10.0") double radiusKm) {
        return findNearbyHubsUseCase.findNearbyAvailableHubs(
                GeoPoint.of(latitude, longitude), radiusKm, tenantId);
    }

    @Operation(summary = "Find the nearest available relay hub to a geographic point")
    @GetMapping("/tenants/{tenantId}/hubs/nearest")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER','AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<RelayHub> findNearestHub(
            @PathVariable UUID tenantId,
            @RequestParam double latitude,
            @RequestParam double longitude) {
        return findNearbyHubsUseCase.findNearestAvailableHub(
                GeoPoint.of(latitude, longitude), tenantId);
    }

    @Operation(summary = "Get relay hub details by ID")
    @GetMapping("/tenants/{tenantId}/hubs/{hubId}")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER','RELAY_OPERATOR','AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<RelayHub> getHub(
            @PathVariable UUID tenantId,
            @PathVariable UUID hubId) {
        return findNearbyHubsUseCase.findHub(hubId, tenantId);
    }

    @Operation(summary = "Update the occupancy count of a relay hub")
    @PatchMapping("/tenants/{tenantId}/hubs/{hubId}/occupancy")
    @PreAuthorize("hasAnyRole('RELAY_OPERATOR','AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<RelayHub> updateHubOccupancy(
            @PathVariable UUID tenantId,
            @PathVariable UUID hubId,
            @RequestParam int newOccupancy) {
        return findNearbyHubsUseCase.updateHubOccupancy(hubId, tenantId, newOccupancy);
    }

    // ─── Geofencing ────────────────────────────────────────────────────────────

    @Operation(summary = "Check whether a point is inside a named service zone")
    @GetMapping("/tenants/{tenantId}/geofence/zone/{zoneId}/check")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER','AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<Boolean> isPointInZone(
            @PathVariable UUID tenantId,
            @PathVariable UUID zoneId,
            @RequestParam double latitude,
            @RequestParam double longitude) {
        return geofenceUseCase.isPointInZone(GeoPoint.of(latitude, longitude), zoneId);
    }

    @Operation(summary = "Check whether a point is covered by any zone of a given agency")
    @GetMapping("/tenants/{tenantId}/geofence/agency/{agencyId}/check")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER','AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<Boolean> isPointCoveredByAgency(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestParam double latitude,
            @RequestParam double longitude) {
        return geofenceUseCase.isPointCoveredByAgency(
                GeoPoint.of(latitude, longitude), agencyId, tenantId);
    }

    @Operation(summary = "Find which service zone contains a given coordinate")
    @GetMapping("/tenants/{tenantId}/geofence/find-zone")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER','AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<UUID> findContainingZone(
            @PathVariable UUID tenantId,
            @RequestParam double latitude,
            @RequestParam double longitude) {
        return geofenceUseCase.findContainingZone(GeoPoint.of(latitude, longitude), tenantId);
    }

    // ─── Points of Interest ────────────────────────────────────────────────────

    @Operation(summary = "Create a new Point of Interest (geocoding anchor)")
    @PostMapping("/tenants/{tenantId}/pois")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<PointOfInterest> createPoi(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreatePoiRequest request) {
        return managePoisUseCase.createPoi(
                tenantId,
                request.name(),
                PoiType.valueOf(request.type()),
                GeoPoint.of(request.latitude(), request.longitude()),
                request.description(),
                request.cityCode());
    }

    @Operation(summary = "Get POI by ID")
    @GetMapping("/tenants/{tenantId}/pois/{poiId}")
    @PreAuthorize("isAuthenticated()")
    public Mono<PointOfInterest> getPoi(
            @PathVariable UUID tenantId,
            @PathVariable UUID poiId) {
        return managePoisUseCase.findPoi(poiId, tenantId);
    }

    @Operation(summary = "List POIs by city code")
    @GetMapping("/tenants/{tenantId}/pois/by-city/{cityCode}")
    @PreAuthorize("isAuthenticated()")
    public Flux<PointOfInterest> listPoisByCity(
            @PathVariable UUID tenantId,
            @PathVariable String cityCode) {
        return managePoisUseCase.findPoisByCity(tenantId, cityCode);
    }

    @Operation(summary = "Find POIs near a geographic point")
    @GetMapping("/tenants/{tenantId}/pois/nearby")
    @PreAuthorize("isAuthenticated()")
    public Flux<PointOfInterest> findPoisNearby(
            @PathVariable UUID tenantId,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5.0") double radiusKm) {
        return managePoisUseCase.findPoisNearby(
                GeoPoint.of(latitude, longitude), radiusKm, tenantId);
    }

    @Operation(summary = "Verify (activate) a Point of Interest")
    @PostMapping("/tenants/{tenantId}/pois/{poiId}/verify")
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<PointOfInterest> verifyPoi(
            @PathVariable UUID tenantId,
            @PathVariable UUID poiId) {
        return managePoisUseCase.verifyPoi(poiId, tenantId);
    }

    // ─── FreelancerOrg geo ─────────────────────────────────────────────────────

    @Operation(summary = "Find FreelancerOrg IDs whose service zone covers a coordinate")
    @GetMapping("/tenants/{tenantId}/freelancer-orgs/in-zone")
    @PreAuthorize("hasAnyRole('CLIENT','AGENCY_MANAGER','TNT_ADMIN')")
    public Flux<String> findFreelancerOrgsInZone(
            @PathVariable UUID tenantId,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "10.0") double radiusKm) {
        return freelancerOrgGeoUseCase.findFreelancerOrgsInZone(latitude, longitude, radiusKm, tenantId);
    }

    @Operation(summary = "Check whether a FreelancerOrg covers a coordinate")
    @GetMapping("/tenants/{tenantId}/freelancer-orgs/{orgId}/covers")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER','AGENCY_MANAGER','TNT_ADMIN')")
    public Mono<Boolean> isInFreelancerOrgZone(
            @PathVariable UUID tenantId,
            @PathVariable String orgId,
            @RequestParam double latitude,
            @RequestParam double longitude) {
        return freelancerOrgGeoUseCase.isInFreelancerOrgZone(orgId, latitude, longitude, tenantId);
    }

    @Operation(summary = "Define / update the service zone polygon for a FreelancerOrg")
    @PostMapping("/tenants/{tenantId}/freelancer-orgs/{orgId}/zone")
    @PreAuthorize("hasAnyRole('FREELANCER_OWNER','TNT_ADMIN')")
    public Mono<ServiceZonePolygon> defineFreelancerOrgZone(
            @PathVariable UUID tenantId,
            @PathVariable String orgId,
            @Valid @RequestBody DefineZoneRequest request) {
        return freelancerOrgGeoUseCase.defineFreelancerOrgZone(
                tenantId, orgId, request.name(),
                request.vertices().stream()
                        .map(v -> GeoPoint.of(v.latitude(), v.longitude()))
                        .toList());
    }

    // ─── Request DTOs ──────────────────────────────────────────────────────────

    /**
     * Request body for POI creation.
     */
    public record CreatePoiRequest(
            @NotBlank String name,
            @NotBlank String type,
            double latitude,
            double longitude,
            String description,
            @NotBlank String cityCode
    ) {}

    /**
     * Request body for defining a FreelancerOrg service zone.
     */
    public record DefineZoneRequest(
            @NotBlank String name,
            List<VertexRequest> vertices
    ) {
        public record VertexRequest(double latitude, double longitude) {}
    }
}
