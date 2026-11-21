package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.DeliveryNeedUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.DeliveryNeedUseCase.PlatformPrice;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Lot C-54 — {@code GET /api/announcements/{id}/platform-price}: the platform price of the calling
 * courier's vehicle for this announcement's parcel, shown as an indication in "Apply".
 */
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class CourierPlatformPriceController {

    private final DeliveryNeedUseCase needs;

    @GetMapping("/{id}/platform-price")
    public Mono<PlatformPrice> platformPrice(@PathVariable UUID id,
                                             @CurrentUser(required = false) TntSecurityContext ctx) {
        return needs.platformPriceForCourier(id, ctx != null ? ctx.userId() : null);
    }
}
