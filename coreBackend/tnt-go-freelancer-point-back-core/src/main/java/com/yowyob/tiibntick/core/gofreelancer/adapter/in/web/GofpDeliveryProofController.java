package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryProofService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryProofService.NewProof;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryProofService.ProofView;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryProof.Kind;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/** Proofs (photos, signature) of a delivery's hand-overs. */
@RestController
@RequestMapping("/api/v1/deliveries/{deliveryId}/proofs")
@RequiredArgsConstructor
public class GofpDeliveryProofController {

    private final DeliveryProofService proofs;

    /** Body: the author and the delivery are never read from it. */
    public record AddProofRequest(Kind kind, UUID fileId, String note, Instant capturedAt) {}

    @PostMapping
    public Mono<ResponseEntity<ProofView>> add(@PathVariable UUID deliveryId,
                                               @RequestBody AddProofRequest body,
                                               @CurrentUser(required = false) TntSecurityContext ctx) {
        UUID caller = caller(ctx);
        return proofs.add(deliveryId, caller, new NewProof(body.kind(), body.fileId(), body.note(), body.capturedAt()))
                .map(p -> ResponseEntity.status(HttpStatus.CREATED).body(p))
                .onErrorResume(IllegalArgumentException.class, e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping
    public Flux<ProofView> list(@PathVariable UUID deliveryId,
                                @CurrentUser(required = false) TntSecurityContext ctx) {
        return proofs.list(deliveryId, caller(ctx));
    }

    private static UUID caller(TntSecurityContext ctx) {
        UUID id = ctx != null ? ctx.userId() : null;
        if (id == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return id;
    }
}
