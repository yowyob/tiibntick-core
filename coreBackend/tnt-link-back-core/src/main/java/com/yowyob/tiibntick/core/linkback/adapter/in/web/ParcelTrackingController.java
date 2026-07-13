package com.yowyob.tiibntick.core.linkback.adapter.in.web;

import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.ParcelTrackingResponse;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.ParcelTrackingResponseMapper;
import com.yowyob.tiibntick.core.linkback.application.port.in.TrackParcelUseCase;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "Track a Link parcel by tracking code (public endpoint)")
    @GetMapping("/track/{trackingCode}")
    public Mono<ParcelTrackingResponse> track(@PathVariable String trackingCode) {
        return trackParcelUseCase.trackByCode(trackingCode)
                .map(delivery -> ParcelTrackingResponseMapper.toResponse(trackingCode, delivery));
    }
}
