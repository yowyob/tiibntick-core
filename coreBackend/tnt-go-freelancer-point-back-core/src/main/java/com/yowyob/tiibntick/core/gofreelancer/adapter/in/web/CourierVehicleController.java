package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.service.CourierVehicleService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.CourierVehicleService.VehicleView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Lot C-54 — the caller's own vehicle (profile resolved from the token, never from the path).
 *
 * <ul>
 *   <li>{@code GET  /api/v1/gofp/freelancer-profiles/me/vehicle}</li>
 *   <li>{@code PUT  /api/v1/gofp/freelancer-profiles/me/vehicle} — {@code {vehicleType, plateNumber}}</li>
 *   <li>{@code POST /api/v1/gofp/freelancer-profiles/me/vehicle/photos?reset=} — multipart {@code file},
 *       JPEG/PNG, one photo per call, four at most</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/gofp/freelancer-profiles/me/vehicle")
@RequiredArgsConstructor
public class CourierVehicleController {

    private final CourierVehicleService vehicles;

    /** Body of {@code PUT /me/vehicle}: the type in either vocabulary (moto… or MOTORBIKE…). */
    public record VehicleBody(String vehicleType, String plateNumber) { }

    @GetMapping
    public Mono<VehicleView> myVehicle(@CurrentUser(required = false) TntSecurityContext ctx) {
        return vehicles.myVehicle(ctx);
    }

    @PutMapping
    public Mono<VehicleView> saveMyVehicle(@RequestBody VehicleBody body,
                                           @CurrentUser(required = false) TntSecurityContext ctx) {
        return vehicles.saveMyVehicle(ctx, body == null ? null : body.vehicleType(),
                body == null ? null : body.plateNumber());
    }

    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<VehicleView> addMyPhoto(@RequestPart("file") FilePart file,
                                        @RequestParam(defaultValue = "false") boolean reset,
                                        @CurrentUser(required = false) TntSecurityContext ctx) {
        return vehicles.addMyPhoto(ctx, file, reset);
    }
}
