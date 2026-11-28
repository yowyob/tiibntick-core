package com.yowyob.tiibntick.core.linkback.adapter.in.web;

import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.ParcelTrackingResponse;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.ParcelTrackingResponseMapper;
import com.yowyob.tiibntick.core.linkback.application.port.in.TrackParcelUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.out.ILinkPositionCache;
import com.yowyob.tiibntick.core.linkback.domain.model.LinkPosition;
import com.yowyob.tiibntick.core.delivery.application.tracking.PublicTrackingRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Generic Link business API. This is the single entry point the Link BFF calls for
 * parcel tracking — it must never reach tnt-delivery-core directly (see coreBackend
 * architecture decision: BFF talks only to its product's Core Backend).
 *
 * <p>Public — no {@code @RequirePermission} guard, mirroring tnt-delivery-core's own
 * {@code DeliveryController.track()} public tracking endpoint.
 *
 * @author Dilane PAFE
 */
@Tag(name = "Link Parcels", description = "Parcel tracking for the TiiBnTick Link product")
@RestController
@RequestMapping("/api/v1/platform/link/parcels")
@RequiredArgsConstructor
public class ParcelTrackingController {

    private final TrackParcelUseCase trackParcelUseCase;
    private final ILinkPositionCache positionCache;
    /** Lot evt-l2 (D-137): same per-IP window as delivery-core's public tracking (the BFF forwards X-Forwarded-For). */
    private final PublicTrackingRateLimiter trackingRateLimiter;

    @Operation(summary = "Track a Link parcel by its parcel tracking code (public, masked, rate-limited per IP)")
    @GetMapping("/track/{trackingCode}")
    public Mono<ParcelTrackingResponse> track(@PathVariable String trackingCode, ServerHttpRequest request) {
        String ip = PublicTrackingRateLimiter.clientIp(request.getHeaders().getFirst("X-Forwarded-For"),
                request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null
                        ? request.getRemoteAddress().getAddress().getHostAddress() : null);
        if (!trackingRateLimiter.tryAcquire(ip)) {
            return Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many tracking lookups, retry in a minute"));
        }
        return trackParcelUseCase.trackByCode(trackingCode)
                .flatMap(delivery -> positionCache.findLatest(delivery.getTenantId(), delivery.getId().toString())
                        .map(position -> ParcelTrackingResponseMapper.toResponse(trackingCode, delivery, position))
                        .defaultIfEmpty(ParcelTrackingResponseMapper.toResponse(trackingCode, delivery, (LinkPosition) null)));
    }
}
