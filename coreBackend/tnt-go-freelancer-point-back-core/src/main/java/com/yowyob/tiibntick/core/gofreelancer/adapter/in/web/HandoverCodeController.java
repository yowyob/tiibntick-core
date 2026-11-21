package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.HandoverCodesDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.service.HandoverCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Lot C-53 — the owner of a delivery need reads its handover codes (see {@link HandoverCodeService}). */
@RestController
@RequiredArgsConstructor
public class HandoverCodeController {

    private final HandoverCodeService handoverCodes;

    @GetMapping("/api/delivery-needs/{id}/handover-codes")
    public Mono<ResponseEntity<HandoverCodesDTO>> handoverCodes(@PathVariable UUID id,
                                                                @CurrentUser(required = false) TntSecurityContext ctx) {
        return handoverCodes.forOwner(id, ctx != null ? ctx.userId() : null)
                .map(codes -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(codes));
    }
}
