package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.payment;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.SelectionChallengeRequest;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.SelectionChallengeResponse;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.SelectionConfirmRequest;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.SelectionConfirmResponse;
import com.yowyob.tiibntick.core.gofreelancer.application.service.SelectionPaymentService;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Paid selection of a response (lot L5, spec §4.1): {@code /selection/challenge} creates the payment intent (the
 * Kernel sends the client a code for an escrow), {@code /selection/confirm} secures the price then elects. The client
 * is the caller (JWT subject), checked against the announcement.
 */
@RestController
@RequestMapping("/api/announcements/{announcementId}/responses/{responseId}/selection")
@RequiredArgsConstructor
public class SelectionPaymentController {

    private final SelectionPaymentService selection;

    @PostMapping("/challenge")
    @RequirePermission(resource = "announcement", action = "elect")
    public Mono<SelectionChallengeResponse> challenge(@PathVariable UUID announcementId,
                                                      @PathVariable UUID responseId,
                                                      @RequestBody(required = false) SelectionChallengeRequest body,
                                                      @CurrentUser(required = false) TntSecurityContext ctx) {
        return caller(ctx).flatMap(user -> selection.challenge(announcementId, responseId, user,
                body != null ? body.paymentMode() : null));
    }

    @PostMapping("/confirm")
    @RequirePermission(resource = "announcement", action = "elect")
    public Mono<SelectionConfirmResponse> confirm(@PathVariable UUID announcementId,
                                                  @PathVariable UUID responseId,
                                                  @Valid @RequestBody SelectionConfirmRequest body,
                                                  @CurrentUser(required = false) TntSecurityContext ctx) {
        return caller(ctx).flatMap(user -> selection.confirm(announcementId, responseId, user, body.intentId(),
                body.challengeToken(), body.code(), body.guaranteeChallengeToken(), body.guaranteeCode()));
    }

    static Mono<UUID> caller(TntSecurityContext ctx) {
        return ctx == null || ctx.userId() == null
                ? Mono.error(new org.springframework.security.access.AccessDeniedException("Authentication required"))
                : Mono.just(ctx.userId());
    }

    @ExceptionHandler(WalletSettlementException.class)
    public Mono<ProblemDetail> onSettlement(WalletSettlementException ex) {
        return Mono.just(PaymentProblems.of(ex));
    }
}
