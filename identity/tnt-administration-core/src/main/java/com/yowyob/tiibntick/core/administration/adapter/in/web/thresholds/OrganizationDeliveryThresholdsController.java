package com.yowyob.tiibntick.core.administration.adapter.in.web.thresholds;

import com.yowyob.tiibntick.core.administration.adapter.in.web.thresholds.DeliveryThresholdsDtos.EffectiveResponse;
import com.yowyob.tiibntick.core.administration.adapter.in.web.thresholds.DeliveryThresholdsDtos.OrganizationRequest;
import com.yowyob.tiibntick.core.administration.adapter.in.web.thresholds.DeliveryThresholdsDtos.OrganizationResponse;
import com.yowyob.tiibntick.core.administration.application.port.in.ManageDeliveryThresholdsUseCase;
import com.yowyob.tiibntick.core.administration.application.port.in.ManageDeliveryThresholdsUseCase.PutOrganizationCommand;
import com.yowyob.tiibntick.core.administration.application.port.in.ResolveDeliveryThresholdsUseCase;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Overrides of the delivery thresholds by a TiiBnTick Agency organization ([B.5], D-087, QO-18; spec §5.3).
 *
 * <p>{@code organization:delivery-settings:write} changes the override, {@code organization:delivery-settings:read}
 * reads the effective values. AGENCY_MANAGER and ORG_ADMIN hold both by default and decide who else in the agency
 * gets them; the caller must belong to the organization, unless it is a platform administrator
 * ({@code tnt:platform:admin}).
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/settings/delivery-thresholds")
@Tag(name = "Organization delivery settings", description = "Delivery thresholds of a TiiBnTick Agency organization")
@SecurityRequirement(name = "bearerAuth")
public class OrganizationDeliveryThresholdsController {

    private final ManageDeliveryThresholdsUseCase thresholds;
    private final ResolveDeliveryThresholdsUseCase resolver;
    private final OrganizationScopeGuard scope;

    public OrganizationDeliveryThresholdsController(ManageDeliveryThresholdsUseCase thresholds,
                                                    ResolveDeliveryThresholdsUseCase resolver,
                                                    OrganizationScopeGuard scope) {
        this.thresholds = thresholds;
        this.resolver = resolver;
        this.scope = scope;
    }

    @GetMapping
    @RequirePermission(resource = "organization:delivery-settings", action = "write")
    @Operation(summary = "The organization's override (every field null when it has none)")
    public Mono<OrganizationResponse> get(@Parameter(hidden = true) @CurrentUser TntUserIdentity caller,
                                          @PathVariable UUID organizationId) {
        return scope.check(caller, organizationId)
                .then(thresholds.getOrganizationOverride(organizationId)
                        .map(OrganizationResponse::from)
                        .defaultIfEmpty(OrganizationResponse.none(organizationId)));
    }

    @PutMapping
    @RequirePermission(resource = "organization:delivery-settings", action = "write")
    @Operation(summary = "Create or replace the override (422 if not an Agency or out of range, 409 if stale)")
    public Mono<OrganizationResponse> put(@Parameter(hidden = true) @CurrentUser TntUserIdentity caller,
                                          @PathVariable UUID organizationId,
                                          @RequestBody OrganizationRequest request) {
        return scope.check(caller, organizationId)
                .then(Mono.defer(() -> thresholds.putOrganizationOverride(new PutOrganizationCommand(organizationId,
                        request.values(), request.version(), caller == null ? null : caller.userId(),
                        caller == null ? null : caller.tenantId()))))
                .map(OrganizationResponse::from);
    }

    @DeleteMapping
    @RequirePermission(resource = "organization:delivery-settings", action = "write")
    @Operation(summary = "Remove the override (the organization inherits the platform values; 404 if none)")
    public Mono<ResponseEntity<Void>> delete(@Parameter(hidden = true) @CurrentUser TntUserIdentity caller,
                                             @PathVariable UUID organizationId) {
        return scope.check(caller, organizationId)
                .then(Mono.defer(() -> thresholds.deleteOrganizationOverride(organizationId,
                        caller == null ? null : caller.userId(), caller == null ? null : caller.tenantId())))
                .map(deleted -> deleted
                        ? ResponseEntity.noContent().<Void>build()
                        : ResponseEntity.notFound().<Void>build());
    }

    @GetMapping("/effective")
    @RequirePermission(resource = "organization:delivery-settings", action = "read")
    @Operation(summary = "Effective thresholds of the organization, each with its source (PLATFORM / ORGANIZATION)")
    public Mono<EffectiveResponse> effective(@Parameter(hidden = true) @CurrentUser TntUserIdentity caller,
                                             @PathVariable UUID organizationId) {
        return scope.check(caller, organizationId)
                .then(Mono.defer(() -> resolver.resolve(organizationId)))
                .map(EffectiveResponse::from);
    }
}
