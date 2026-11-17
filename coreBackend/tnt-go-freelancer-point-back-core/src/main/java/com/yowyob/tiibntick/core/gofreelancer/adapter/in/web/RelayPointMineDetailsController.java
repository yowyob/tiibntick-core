package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.OpeningHoursDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.service.RelayPointAccessGuard;
import com.yowyob.tiibntick.core.gofreelancer.application.service.RelayPointHoursService;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.OpeningHours;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayPointVisuals;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Lot C-49 — opening hours and photos of the caller's own relay point, so that the owner's phone
 * reads them back from the server. 404 when the caller has no relay point.
 */
@RestController
@RequestMapping("/api/v1/gofp/relay-point-profiles/mine")
@RequiredArgsConstructor
public class RelayPointMineDetailsController {

    private final RelayPointAccessGuard guard;
    private final RelayPointHoursService service;

    private Mono<GofpRelayPoint> mine(TntSecurityContext ctx) {
        return guard.myRelayPoints(ctx).next();
    }

    @GetMapping("/opening-hours")
    public Mono<ResponseEntity<List<OpeningHours>>> getMyOpeningHours(
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return mine(ctx)
                .flatMap(rp -> service.hoursOf(rp.getCoreRelayPointId()).collectList())
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/opening-hours")
    public Mono<ResponseEntity<List<OpeningHours>>> replaceMyOpeningHours(
            @RequestBody List<OpeningHoursDTO> week,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return mine(ctx)
                .flatMap(rp -> service.replaceHours(rp.getCoreRelayPointId(), week).collectList()
                        .map(ResponseEntity::ok)
                        .onErrorResume(IllegalArgumentException.class,
                                e -> Mono.just(ResponseEntity.badRequest().<List<OpeningHours>>build())))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/visuals")
    public Mono<ResponseEntity<RelayPointVisuals>> getMyVisuals(
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return mine(ctx)
                .flatMap(rp -> service.visualsOf(rp.getCoreRelayPointId()))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
