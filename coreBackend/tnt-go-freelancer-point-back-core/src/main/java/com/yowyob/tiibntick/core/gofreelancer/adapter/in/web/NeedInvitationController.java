package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.DeliveryNeedResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.NeedInvitationDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.service.NeedInvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Lot C-52 — invitations: the client proposes his open delivery need to one courier, who accepts or
 * declines within {@link NeedInvitationService#TTL}.
 */
@RestController
@RequiredArgsConstructor
public class NeedInvitationController {

    private final NeedInvitationService invitations;

    public record InviteBody(UUID freelancerId) {}

    public record DeclineBody(String reason) {}

    private static UUID callerId(TntSecurityContext ctx) {
        return ctx != null ? ctx.userId() : null;
    }

    @PostMapping("/api/delivery-needs/{id}/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<NeedInvitationDTO> invite(@PathVariable UUID id, @RequestBody InviteBody body,
                                          @CurrentUser(required = false) TntSecurityContext ctx) {
        return invitations.invite(id, body == null ? null : body.freelancerId(), callerId(ctx));
    }

    @GetMapping("/api/delivery-needs/{id}/invitations")
    public Flux<NeedInvitationDTO> forNeed(@PathVariable UUID id,
                                           @CurrentUser(required = false) TntSecurityContext ctx) {
        return invitations.forNeed(id, callerId(ctx));
    }

    @GetMapping("/api/v1/gofp/invitations/mine")
    public Flux<NeedInvitationDTO> mine(@CurrentUser(required = false) TntSecurityContext ctx) {
        return invitations.mine(callerId(ctx));
    }

    @PostMapping("/api/v1/gofp/invitations/{id}/accept")
    public Mono<DeliveryNeedResponseDTO> accept(@PathVariable UUID id,
                                                @CurrentUser(required = false) TntSecurityContext ctx) {
        return invitations.accept(id, callerId(ctx));
    }

    @PostMapping("/api/v1/gofp/invitations/{id}/decline")
    public Mono<NeedInvitationDTO> decline(@PathVariable UUID id, @RequestBody(required = false) DeclineBody body,
                                           @CurrentUser(required = false) TntSecurityContext ctx) {
        return invitations.decline(id, callerId(ctx), body == null ? null : body.reason());
    }
}
