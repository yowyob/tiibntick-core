package com.yowyob.tiibntick.core.administration.adapter.in.web.thresholds;

import com.yowyob.tiibntick.core.administration.adapter.in.web.thresholds.DeliveryThresholdsDtos.OrganizationResponse;
import com.yowyob.tiibntick.core.administration.adapter.in.web.thresholds.DeliveryThresholdsDtos.PlatformRequest;
import com.yowyob.tiibntick.core.administration.adapter.in.web.thresholds.DeliveryThresholdsDtos.PlatformResponse;
import com.yowyob.tiibntick.core.administration.application.port.in.ManageDeliveryThresholdsUseCase;
import com.yowyob.tiibntick.core.administration.application.port.in.ManageDeliveryThresholdsUseCase.UpdatePlatformCommand;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.RelayStorageBounds;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Platform delivery thresholds, set by the TiiBnTick administrator for the whole platform ([B.5], QO-8, QO-18,
 * QO-29; spec §5.3). Not per tenant. Permission {@code tnt:platform:admin} (TNT_ADMIN only).
 */
@RestController
@RequestMapping("/api/v1/admin/settings/delivery-thresholds")
@Tag(name = "Administration", description = "TiiBnTick platform administration: permissions, roles, settings")
@SecurityRequirement(name = "bearerAuth")
public class PlatformDeliveryThresholdsController {

    private final ManageDeliveryThresholdsUseCase thresholds;

    public PlatformDeliveryThresholdsController(ManageDeliveryThresholdsUseCase thresholds) {
        this.thresholds = thresholds;
    }

    @GetMapping
    @RequirePermission(resource = "tnt:platform", action = "admin")
    @Operation(summary = "Platform delivery thresholds (attempts, relay storage, reminder, failed returns, late alerts)")
    public Mono<PlatformResponse> get() {
        return thresholds.getPlatform().map(PlatformResponse::from);
    }

    @PutMapping
    @RequirePermission(resource = "tnt:platform", action = "admin")
    @Operation(summary = "Replace the platform delivery thresholds (409 if version is stale, 422 if out of range)")
    public Mono<PlatformResponse> update(@Parameter(hidden = true) @CurrentUser TntUserIdentity caller,
                                         @Valid @RequestBody PlatformRequest request) {
        return thresholds.updatePlatform(new UpdatePlatformCommand(request.values(),
                        new RelayStorageBounds(request.independentRelayStorageMinDays(),
                                request.independentRelayStorageMaxDays()),
                        request.version(), caller == null ? null : caller.userId(),
                        caller == null ? null : caller.tenantId()))
                .map(PlatformResponse::from);
    }

    @GetMapping("/organizations")
    @RequirePermission(resource = "tnt:platform", action = "admin")
    @Operation(summary = "Every organization override of the delivery thresholds")
    public Mono<List<OrganizationResponse>> listOverrides() {
        return thresholds.listOrganizationOverrides().map(OrganizationResponse::from).collectList();
    }
}
